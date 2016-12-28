#!/usr/bin/groovy

// Runs a shell command in the vagrant vm
def runInVagrant = { String workspace, String command ->
    sh "cd ${workspace} && vagrant ssh -c '${command}'"
}

node {
    String workspace = pwd()
    try {
        // Store the directory we are executed in as our workspace.
        // These are the build parameters we want to use
        String buildParams = "-DENABLE_TEST=ON -DENABLE_COVERAGE=ON "
        buildParams       += "-DENABLE_USER_DOC=ON -DENABLE_API_DOC=ON "
        buildParams       += "-DENABLE_SYSTEMD=ON -DENABLE_PROFILING=ON "
        buildParams       += "-DENABLE_EXAMPLES=ON -DCMAKE_INSTALL_PREFIX=/usr"

        // Stages are subtasks that will be shown as subsections of the finiished build in Jenkins.
        stage('Download') {
            // Checkout the git repository and refspec pointed to by jenkins
            checkout scm
            // Update the submodules in the repository.
            sh 'git submodule update --init'
        }

        stage('StartVM') {
            // Calculate available amount of RAM
            String gigsramStr = sh (
                script: 'free -tg | tail -n1 | awk \'{ print $2 }\'',
                returnStdout: true
            )
            int gigsram = gigsramStr.trim() as Integer
            // Cap memory usage at 8GB
            if (gigsram >= 8) {
                gigsram = 8
                println "Will set VAGRANT_RAM to ${gigsram}"
            }

            // Start the machine (destroy it if present) and provision it
            sh "cd ${workspace} && vagrant destroy -f || true"
            withEnv(["VAGRANT_RAM=${gigsram}",
                     "APT_CACHE_SERVER=10.8.36.16"]) {
                sh "cd ${workspace} && vagrant up"
            }
        }
    }

    catch(err) {
        // Do not add a stage here.
        // When "stage" commands are run in a different order than the previous run
        // the history is hidden since the rendering plugin assumes that the system has changed and
        // that the old runs are irrelevant. As such adding a stage at this point will trigger a
        // "change of the system" each time a run fails.
        println "Something went wrong!"
        currentBuild.result = "FAILURE"
    }

    // Always try to shut down the machine
    // Shutdown the machine
    sh "cd ${workspace} && vagrant destroy -f || true"
}
