pipeline {
    agent any
    parameters {
        string(name: 'manifest', defaultValue: '', description: 'What repo manifest to use')
        string(name: 'bitbake_image', defaultValue: '', description: 'What image to build')
    }
    stages {
        stage('Download') {
	    steps {
                // Checkout the git repository and refspec pointed to by jenkins
                checkout scm
                // Update the submodules in the repository.
                sh "git submodule update --init"
	    }
	}
        stage('build') {
	    steps {
	        script {
                    // Calculate available amount of RAM
                    String gigsramStr = sh (
                        script: "free -tg | tail -n1 | awk '{ print \$2 }'",
                        returnStdout: true
                    )
                    int gigsram = gigsramStr.trim() as Integer
                    // Cap memory usage at 8GB
                    if (gigsram >= 8) {
                        gigsram = 8
                        println "Will set VAGRANT_RAM to ${gigsram}"
                    }
                 
                    // Start the machine (destroy it if present) and provision it
                    sh "cd ${workspace} && MANIFEST=${params.manifest} BITBAKE_IMAGE=${params.bitbake_image} vagrant destroy -f || true"
                    withEnv(["VAGRANT_RAM=${gigsram}",
                             "APT_CACHE_SERVER=10.8.36.16"]) {
                        sh "cd ${workspace} && MANIFEST=${params.manifest} BITBAKE_IMAGE=${params.bitbake_image} vagrant up"
                    }
		}
	    }
	}
    }
    post {
        always {
            // Shutdown the machine
            sh "cd ${workspace} && MANIFEST= BITBAKE_IMAGE= vagrant destroy -f || true"
	}
    }
}
