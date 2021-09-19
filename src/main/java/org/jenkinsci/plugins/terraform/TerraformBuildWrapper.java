package org.jenkinsci.plugins.terraform;


import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Extension;
import hudson.CopyOnWrite;

import hudson.util.ListBoxModel;
import hudson.util.ArgumentListBuilder;

import hudson.model.Computer;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.AbstractProject;
import hudson.model.EnvironmentContributingAction;

import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import org.jenkins_ci.plugins.run_condition.core.BooleanCondition;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import java.io.PrintWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.FileNotFoundException;

class VariableInjectionAction implements EnvironmentContributingAction {

    private String key;
    private String value;

    public VariableInjectionAction(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public void buildEnvVars(AbstractBuild build, EnvVars envVars) {

        if (envVars != null && key != null && value != null) {
            envVars.put(key, value);
        }
    }

    public String getDisplayName() {
        return "VariableInjectionAction";
    }

    public String getIconFileName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }
}



public class TerraformBuildWrapper extends BuildWrapper {

    private final Configuration config;
    private final String destroyCondition;
    private final String environmentVariables;
    private final String terraformInstallation;
    private final String variables;
    private final boolean doDestroy;
    private final boolean doInit;
    private final boolean doGetUpdate;
    private final boolean doNotApply;
    private final boolean doNotLock;
    private final boolean useColorizedStdout;
    private final boolean useRemoteState;
    private FilePath stateFile;
    private FilePath configFile;
    private FilePath variablesFile;
    private FilePath workspacePath;
    private FilePath workingDirectory;

    private static final String WORK_DIR_NAME = "terraform-plugin";
    private static final String CONFIG_FILE_NAME = "terraform";
    private static final String STATE_FILE_NAME = "terraform-plugin.tfstate";
    private static final String ENVIRONMENT_VARIABLES = "TF_IN_AUTOMATION=true";
    private static final Logger LOGGER = Logger.getLogger(TerraformBuildWrapper.class.getName());


    @DataBoundConstructor
    public TerraformBuildWrapper(
            Configuration config,
            boolean doDestroy,
            boolean doGetUpdate,
            boolean doInit,
            boolean doNotApply,
            boolean doNotLock,
            boolean useColorizedStdout,
            boolean useRemoteState,
            String destroyCondition,
            String environmentVariables,
            String terraformInstallation,
            String variables) {
        this.config = config;
        this.doDestroy = doDestroy;
        this.doGetUpdate = doGetUpdate;
        this.doInit = doInit;
        this.doNotApply = doNotApply;
        this.doNotLock = doNotLock;
        this.useColorizedStdout = useColorizedStdout;
        this.useRemoteState = useRemoteState;
        this.destroyCondition = destroyCondition;
        this.environmentVariables = environmentVariables;
        this.terraformInstallation = terraformInstallation;
        this.variables = variables == null ? "" : variables;
    }


    public Configuration getConfig() {
        return this.config;
    }


    public Configuration.Mode getMode() {
        return this.config.getMode();
    }


    public String getInlineConfig() {
        return this.config.getInlineConfig();
    }


    public String getFileConfig() {
        return this.config.getFileConfig();
    }


    public String getConfigMode() {
        return this.config.getValue();
    }


    public String getTerraformWorkspace() { return this.config.getTerraformWorkspace(); }


    public boolean getUseTerraformWorkspace() {
        return this.config.getUseTerraformWorkspace();
    }


    public boolean doGetUpdate() {
        return this.doGetUpdate;
    }


    public boolean getDoGetUpdate() {
        return this.doGetUpdate;
    }


    public boolean getDoInit() {
        return this.doInit;
    }


    public boolean getDoNotApply() {
        return this.doNotApply;
    }


    public boolean getDoNotLock() {
        return this.doNotLock;
    }


    public boolean getDoDestroy() {
        return this.doDestroy;
    }


    public boolean getUseColorizedStdout() {
        return this.useColorizedStdout;
    }


    public boolean getUseRemoteState() {
        return this.useRemoteState;
    }


    public String getDestroyCondition() { return this.destroyCondition == null ? "" : this.destroyCondition; }


    public String getEnvironmentVariables() { return this.environmentVariables == null ? "" : this.environmentVariables; }


    public String getTerraformInstallation() {
        return this.terraformInstallation;
    }


    public String getVariables() {
        return this.variables == null ? "" : this.variables;
    }


    public TerraformInstallation getInstallation() {
        for (TerraformInstallation installation : ((DescriptorImpl) getDescriptor()).getInstallations()) {
            if (terraformInstallation != null &&
                installation.getName().equals(terraformInstallation)) {
                return installation;
            }
        }
        return null;
    }


    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }


    public String getExecutable(EnvVars env, BuildListener listener, Launcher launcher) throws IOException, InterruptedException {
        String executablePath = null;
        try {
            TerraformInstallation terraform = getInstallation().forNode(Computer.currentComputer().getNode(), listener).forEnvironment(env);
            executablePath = terraform.getExecutablePath(launcher);
        } catch (NullPointerException ex) {
            throw new IOException(Messages.InstallationNotFound());
        }
        return executablePath;
    }


    public void executeGet(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = build.getEnvironment(listener);

        String executable = getExecutable(env, listener, launcher);
        args.add(executable);

        args.add("get");

        if (getDoGetUpdate()) {
            args.add("-update");
        }

        if (!getUseColorizedStdout()) {
            args.add("-no-color");
        }

        LOGGER.info("Launching Terraform get: "+args.toString());

        int result = launcher.launch()
                .pwd(workspacePath)
                .cmds(args)
                .stdout(listener)
                .envs(promoteEnvVars(env))
                .join();

        if (result != 0) {
            throw new Exception("Terraform get failed: "+ result);
        }
    }


    public void executeInit(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = build.getEnvironment(listener);

        String executable = getExecutable(env, listener, launcher);
        args.add(executable);

        args.add("init");

        args.add("-input=false");

        if (doGetUpdate) {
            args.add("-upgrade=true");
        }

        if (getDoNotLock()) {
            args.add("-lock=false");
        }

        if (!getUseColorizedStdout()) {
            args.add("-no-color");
        }

        LOGGER.info("Launching Terraform init: " + args.toString());

        int result = launcher.launch()
                .pwd(workspacePath)
                .cmds(args)
                .stdout(listener)
                .envs(promoteEnvVars(env))
                .join();

        if (result != 0) {
            throw new Exception("Terraform init failed: "+ result);
        }
    }


    public void executeWorkspace(AbstractBuild build, final Launcher launcher, final BuildListener listener, final String command) throws Exception {
        String workspace = TokenMacro.expandAll(build, listener, getTerraformWorkspace());

        // Ignore workspace management if not configured.
        if (workspace.isEmpty()) {
            return;
        }

        // Don't "new" or "delete" the "default" workspace.
        if (!command.equals("select") && workspace.equals("default")) {
            return;
        }

        // Recursive request to create a new workspace prior to selecting. If already exists, this is benign.
        if (command.equals("select")) {
            executeWorkspace(build, launcher, listener, "new");
        }

        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = build.getEnvironment(listener);

        String executable = getExecutable(env, listener, launcher);
        args.add(executable);

        args.add("workspace");

        args.add(command);

        if (command == "new" || command == "delete" ) {
            if (getDoNotLock()) {
                args.add("-lock=false");
            }
        }

        if (command == "delete") {
            args.add("-force");
        }

        if (command == "select") {
            build.addAction(new VariableInjectionAction("TF_WORKSPACE", workspace));
        }

        args.add(workspace);

        LOGGER.info("Launching Terraform workspace: "+args.toString());

        launcher.launch()
                .pwd(workspacePath)
                .cmds(args)
                .stdout(listener)
                .envs(promoteEnvVars(env))
                .join();
    }


    public void executeApply(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws Exception {
        ArgumentListBuilder args = new ArgumentListBuilder();
        EnvVars env = build.getEnvironment(listener);

        String executable = getExecutable(env, listener, launcher);
        args.add(executable);

        args.add("apply");
        args.add("-input=false");
        args.add("-auto-approve");
        if (!getUseRemoteState()) {
            args.add("-state="+stateFile.getRemote());
        }

        if (getDoNotLock()) {
            args.add("-lock=false");
        }

        if (!isNullOrEmpty(getVariables())) {
            variablesFile = workingDirectory.createTextTempFile("variables", ".tfvars", TokenMacro.expandAll(build, listener, getVariables()));
            args.add("-var-file="+variablesFile.getRemote());
        }

        if (!getUseColorizedStdout()) {
            args.add("-no-color");
        }

        LOGGER.info("Launching Terraform apply: "+args.toString());

        int result = launcher.launch()
                .pwd(workspacePath)
                .cmds(args)
                .stdout(listener)
                .envs(promoteEnvVars(env))
                .join();

        if (result != 0) {
            throw new Exception("Terraform apply failed: "+ result);
        }
    }

    @Override
    public Environment setUp(AbstractBuild build, final Launcher launcher, final BuildListener listener) throws IOException, InterruptedException {
        try {
            // get executable and var-file from environment
            EnvVars env = build.getEnvironment(listener);
            setupWorkspace(build, listener, env);

            if (getMode() == Configuration.Mode.FILE) {
                executeWorkspace(build, launcher, listener, "select");
            }

            String executable = getExecutable(env, listener, launcher);
            String variables = getVariables();
            variables = TokenMacro.expandAll(build, listener, variables);
            variablesFile = workingDirectory.createTextTempFile("variables", ".tfvars", variables);
            // Create actions to inject environment variables
            VariableInjectionAction tfbinAction = new VariableInjectionAction("TF_BIN", executable);
            VariableInjectionAction tfvarAction = new VariableInjectionAction("TF_VAR", variablesFile.getRemote());
            // Inject environment variables
            build.addAction(tfbinAction);
            build.addAction(tfvarAction);

            if (getDoInit()) {
                executeInit(build, launcher, listener);
            }

            executeGet(build, launcher, listener);

            if (! getDoNotApply()) {
              executeApply(build, launcher, listener);
            }
        } catch (Exception ex) {
            LOGGER.severe(exceptionToString(ex));
            listener.fatalError(exceptionToString(ex));
            deleteTemporaryFiles();
            return null;
        }

        return new Environment() {

            @Override
            public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {

                if (getDoDestroy()) {

                    try {
                        String condition = getDestroyCondition();
                        boolean destroyConditionPass = condition.isEmpty() ||
                                new BooleanCondition(condition).runPerform(build, listener);

                        if (destroyConditionPass) {
                            EnvVars env = build.getEnvironment(listener);

                            ArgumentListBuilder args = new ArgumentListBuilder();
                            args.add(getExecutable(env, listener, launcher));

                            args.add("destroy");

                            args.add("-input=false");
                            args.add("-auto-approve");

                            if (!getUseRemoteState()) {
                                args.add("-state=" + stateFile.getRemote());
                            }

                            if (!isNullOrEmpty(getVariables())) {
                                args.add("-var-file=" + variablesFile.getRemote());
                            }

                            if (!getUseColorizedStdout()) {
                                args.add("-no-color");
                            }

                            LOGGER.info("Launching Terraform destroy: " + args.toString());

                            int result = launcher.launch()
                                    .pwd(workspacePath)
                                    .cmds(args)
                                    .stdout(listener)
                                    .envs(promoteEnvVars(env))
                                    .join();

                            if (result != 0) {
                                deleteTemporaryFiles();
                                return false;
                            }

                            if (getMode() == Configuration.Mode.FILE) {
                                executeWorkspace(build, launcher, listener, "delete");
                            }
                        }
                    } catch (Exception ex) {
                        LOGGER.severe(exceptionToString(ex));
                        listener.fatalError(exceptionToString(ex));
                        deleteTemporaryFiles();
                        return false;
                    }
                }

                deleteTemporaryFiles();

                return true;
            }
        };
    }


    private String[] promoteEnvVars(EnvVars env) {
        String executionEnvironment = (environmentVariables == null ? "" : environmentVariables) + "\n" + ENVIRONMENT_VARIABLES;

        // Split flat string into array of line-delimited string of name=value or name variables.
        String envs[] = executionEnvironment.split("\\r?\\n\\r?");

        List<String> result = new LinkedList<>();
        // In-place replacement of <name> with build env value of matching name. If both
        // execution <environmentVariables> and build <env> have variable, the environmentVariable
        // wins as it is more explicitly scoped.
        for (int i = 0; i < envs.length; ++i) {
            String var = envs[i];
            if (var.isEmpty()) continue;
            if (var.indexOf('=') == -1) {
                String value = env.get(var);
                if (value != null) {
                    var = var + '=' + value;
                }
            }

            result.add(var);
        }

        return result.toArray(new String[0]);
    }


    private void setupWorkspace(AbstractBuild build, final BuildListener listener, EnvVars env) throws FileNotFoundException, Exception {
        switch (getMode()) {
            case INLINE:
                workingDirectory = new FilePath(build.getWorkspace(), WORK_DIR_NAME);
                String inlineConfig = getInlineConfig();
                inlineConfig = TokenMacro.expandAll(build, listener, inlineConfig);
                configFile = workingDirectory.createTextTempFile(CONFIG_FILE_NAME, ".tf", inlineConfig);
                workspacePath = workingDirectory;
                if (configFile == null || !configFile.exists()) {
                    throw new FileNotFoundException(Messages.ConfigurationNotCreated());
                }
                break;
            case FILE:
                String configPath = getFileConfig();
                if (!isNullOrEmpty(configPath)) {
                    configPath = TokenMacro.expandAll(build, listener, configPath);
                    workspacePath = new FilePath(build.getWorkspace(), configPath);
                    if (!workspacePath.isDirectory()) {
                        throw new FileNotFoundException(Messages.ConfigurationPathNotFound(workspacePath));
                    }
                } else {
                    workspacePath = build.getWorkspace();
                }

                if (getUseTerraformWorkspace()) {
                    workingDirectory = new FilePath(build.getWorkspace(), configPath + "/.terraform");
                }
                else {
                    workingDirectory = new FilePath(build.getWorkspace(), WORK_DIR_NAME);
                }

                break;
            default:
                throw new Exception(Messages.InvalidConfigMode());
        }

        if (!getUseRemoteState()) {
            stateFile = new FilePath(workingDirectory, STATE_FILE_NAME);
        }

        build.addAction(new VariableInjectionAction("TF_CWD", workspacePath.getRemote()));
    }


    private void deleteTemporaryFiles() throws IOException, InterruptedException {
        if (variablesFile != null && variablesFile.exists())
            variablesFile.delete();

        if (configFile != null && configFile.exists())
            configFile.delete();
    }


    private boolean isNullOrEmpty(String value) {
        return (value == null || value.trim().isEmpty()) ? true : false;
    }


    private String exceptionToString(Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }


    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @CopyOnWrite
        private volatile TerraformInstallation[] installations = new TerraformInstallation[0];


        public DescriptorImpl() {
            super(TerraformBuildWrapper.class);
            load();
        }


        public TerraformInstallation[] getInstallations() {
            return this.installations;
        }


        public void setInstallations(TerraformInstallation[] installations) {
            this.installations = installations;
            save();
        }


        public ListBoxModel doFillTerraformInstallationItems() {
            ListBoxModel m = new ListBoxModel();
            for (TerraformInstallation inst : installations) {
                m.add(inst.getName());
            }
            return m;
        }


        public boolean isInlineConfigChecked(TerraformBuildWrapper instance) {
            boolean result = true;
            if (instance != null)
                return (instance.getInlineConfig() != null);

            return result;
        }


        public boolean isFileConfigChecked(TerraformBuildWrapper instance) {
            boolean result = false;
            if (instance != null)
                return (instance.getFileConfig() != null);

            return result;
        }

        public boolean isDoDestoryConfigChecked(TerraformBuildWrapper instance) {
            boolean result = false;
            if (instance != null)
                return (instance.getDoDestroy());

            return result;
        }

        public boolean isApplicable(AbstractProject<?, ?> project) {
            return true;
        }


        public String getDisplayName() {
            return Messages.BuildWrapperName();
        }
    }
}
