/*************************************************************************
**** Description :: This groovy code is used for JAVA Maven pipeline  ****
**** Created By  :: Pramod Vishwakarma                                ****
**** Created On  :: 12/14/2018                                        ****
**** version     :: 1.0                                               ****
**************************************************************************/
import com.sym.devops.scm.*
import com.sym.devops.build.maven.*
import com.sym.devops.sonar.*
import com.sym.devops.notification.*

def call(body) 
{
   def config = [:]
   body.resolveStrategy = Closure.DELEGATE_FIRST
   body.delegate = config
   body()
   timestamps {
     try {
        def mav = new maven()
        currentBuild.result = "SUCCESS"
        NEXT_STAGE = "none"
        branch_name = new ChoiceParameterDefinition('BRANCH', ['development','master'] as String[],'')
        value = input(message: 'Please select specified inputs', parameters: [branch_name])
        if(value == 'development') {
               LINUX_CREDENTIALS = 'LINUX-DEV-KEY'
               DEPLOYMENT_SERVERS = '192.168.56.102'
               BRANCH = 'development'
        }
	if(value == 'master') {
               LINUX_CREDENTIALS = 'LINUX-DEV-KEY'
               DEPLOYMENT_SERVERS = '192.168.56.102'
	       BRANCH = 'master'
	}
        stage ('\u2776 Code Checkout') {
           def git = new git()
           git.Checkout("${config.GIT_URL}","${BRANCH}","${config.GIT_CREDENTIALS}")
        }
	stage ('\u2777 Sonar Analysis') { 
           def s = new sonar()        
	   s.javaJSSonarAnalysis("${config.SONAR_PROPERTY}")
	   NEXT_STAGE='maven_build'
        }  
        stage ('\u2778 Build Tasks') {
          parallel (
            "\u2460 Maven Build" : {
               while (NEXT_STAGE != "maven_build") {
                 continue
               }
	       mav.mavenBuild("${config.MAVEN_HOME}","${config.MAVEN_GOAL}")
               NEXT_STAGE='clean_package'
            },
            "\u2461 Clean Package" : {
               while (NEXT_STAGE != "clean_package") {
                 continue
               }
               mav.cleanBuildPackage("${config.BRAND_NAME}","${config.BUILD_PACKAGE_DIRECTORY}")
               NEXT_STAGE='create_package'
            },
            "\u2462 Create Package" : {
               while (NEXT_STAGE != "create_package") {
                 continue
               }
               mav.createPackage("${config.BRAND_NAME}","${config.BUILD_PACKAGE_DIRECTORY}")
               NEXT_STAGE='send_alert'
           },
           failFast: true
         )
       }
       stage('\u2779 Post-Build Tasks') {
         parallel (
           "\u2461 Deployment Alert" : {
             while (NEXT_STAGE != 'send_alert') {
              continue
             }
             def e = new email()
             e.sendDeployEmail("${config.BRAND_NAME}","${DEPLOYMENT_SERVERS}")
             },
           failFast: true
         )
       }
     }
     catch (Exception caughtError) {
        wrap([$class: 'AnsiColorBuildWrapper']) {
            print "\u001B[41mERROR => sym pipeline failed, check detailed logs..."
            currentBuild.result = "FAILURE"
            throw caughtError
        }
     }
     finally {
        def g = new git()
         def e = new email()
         String BODY = new File("${WORKSPACE}/${config.EMAIL_TEMPLATE}").text
       e.sendemail("${currentBuild.result}","$BODY","${config.RECIPIENT}","${DEPLOYMENT_SERVERS}")
     }

   }
}
