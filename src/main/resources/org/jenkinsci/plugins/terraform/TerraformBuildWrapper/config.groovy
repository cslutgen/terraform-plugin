package org.jenkinsci.plugins.terraform.TerraformBuildWrapper;

f = namespace('/lib/form')


f.block() {
    f.div(style: "margin: 0px 0px") {
        f.table(style: "width: 100%") {
            f.entry(field: 'terraformInstallation', title: _('Terraform Installation')) {
                f.select();
            }

            f.entry(field:'doInit', title: _('Initialize provider')) {
                f.checkbox();
            }

            f.entry(field:'doGetUpdate', title: _('Update modules'), description: 'Run terraform get with -update flag') {
                f.checkbox();
            }

            f.entry(field:'doNotApply', title: _('Do not apply automatically'), description: 'Do everything except apply') {
                f.checkbox();
            }

            f.radioBlock(checked: descriptor.isInlineConfigChecked(instance), name: 'config', value: 'inline', title: 'Configuration Text') {
                f.entry(title: 'Terraform Text Configuration', field: 'inlineConfig', description: 'Inline configuration') {
                    f.textarea();
                }
            }

            f.radioBlock(checked: descriptor.isFileConfigChecked(instance), name: 'config', value: 'file', title: 'Configuration Path') {
                f.entry(title: 'Terraform Root Module Path', field: 'fileConfig', description: 'Relative Path to workspace directory containing configuration files for the Root Module (TF_CWD)') {
                    f.textbox();
                }

                f.entry(field:'useTerraformWorkspace', title: _('Use Terraform Workspace'), description: 'Use Terraform-style ${TF_CWD}/.terraform (selected) or Jenkins-style ${WORKSPACE}/terraform-plugin (not selected)') {
                    f.checkbox();
                }

                f.entry(field: 'terraformWorkspace', title: _('Workspace Name (Optional)'), description: 'Run terraform workspace command prior to apply. Leave blank to skip workspace selection.') {
                    f.textbox();
                }
            }

            f.entry(field: 'variables', title: _('Resource Variables (Optional)'), description: 'Resource variables will be passed to Terraform as a file (TF_VAR)') {
                f.textarea();
            }

            f.advanced() {
                f.entry(field: 'environmentVariables', title: _('Environment Variables (Optional)'), description: 'Environment variables will be passed to Terraform command line') {
                    f.textarea();
                }

                f.entry(field: 'doDestroy', title: _('Destroy On Build Completion'), description: 'Run destroy command to delete infrastructure on build completion') {
                    f.checkbox();
                }

                f.entry(field: 'destroyCondition', title: _('Destroy if Condition (Optional)'), description: 'Run destroy command only if condition is met') {
                    f.textbox();
                }

                f.entry(field:'doNotLock', title: _('Do not use state locking'),
                        description: 'Skip state locking') {
                    f.checkbox();
                }

                f.entry(field: 'useRemoteState', title: _('Use Remote State'),
                        description: 'Use Terraform backend provider remote state') {
                    f.checkbox();
                }

                f.entry(field: 'useColorizedStdout', title: _('Colorized stdout')) {
                    f.checkbox();
                }
            }
        }
    }
}
