#!groovy

import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*

def call(Map map) {
    echo "开始构建，进入主方法..."
    pipeline {
        agent {
            node {
                label "${map.pipeline_agent_lable}"
                //label 'maven'
            }
        }
    //    parameters {
    //        choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.dev],description: '选择部署方式  1.release 2.dev分支')
    //    }

        environment {
            DOCKER_CREDENTIAL_ID = "${map.docker_credential_id}"                // docker容器镜像仓库账号信任id
            REGISTRY = "${map.registry}"                                        // docker镜像仓库注册地址
            DOCKER_REPO_NAMESPACE = "${map.docker_repo_namespace}"              // docker仓库命名空间名称
            DEPLOY_NAMESPACE = "${map.deploy_namespace}"                        // 部署的项目名称
            PIPELINE_AGENT_LABLE = "${map.pipeline_agent_lable}"                // 工作容器的标签,跑流水线的容器
            JDK_VERSION = "${map.jdk_version}"                                  // jdk版本
            BUILD_TYPE = "${map.build_type}"                                    // 编译方式
            //DEPLOY_FOLDER = "${map.deploy_folder}"                              // 服务器上部署所在的文件夹名称
            SETTING_FILE = "${map.setting_file}"
            KUBECONFIG_CREDENTIAL_ID = "${map.kubeconfig_credential_id}"
            NACOS_SVC = "${map.nacos_svc}"
            NACOS_NAMESPACE = "${map.nacos_namespace}"
            FILE_UPLOAD = "${map.file_upload}"
            IMAGES = "${map.images}"                                            // 需要额外拉取的镜像
            K8S_APPLY = "${map.k8s_apply}"
            K8S_APPLY_SIDECAR = "${map.k8s_apply_sidecar}"
            IS_SIDECAR = "${map.is_sidecar}"
            MODULES = "${map.modules}"

            COMMIT_ID_SHORT = sh(returnStdout: true, script: 'git log --oneline -1 | awk \'{print \$1}\'')
            COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse  HEAD')
            CREATE_TIME = sh(returnStdout: true, script: 'date "+%Y-%m-%d %H:%M:%S"')
        }

        options {
            //失败重试次数
            retry(0)
            //超时时间 job会自动被终止
            timeout(time: 30, unit: 'MINUTES')
            //不允许同一个job同时执行流水线,可被用来防止同时访问共享资源等
            disableConcurrentBuilds()
            //如果某个stage为unstable状态，则忽略后面的任务，直接退出
            skipStagesAfterUnstable()
            //安静的时期 设置管道的静默时间段（以秒为单位），以覆盖全局默认值
            quietPeriod(3)
            //删除隐式checkout scm语句
            skipDefaultCheckout()
        }

        stages {
            stage('checkout scm') {
                steps {
                    script {
                        echo 'checkout(scm)'
                        checkout(scm)
                    }
                }
            }

            stage("init perameter") {
                steps {
                    script {
                        def pomFile = readFile(file: 'pom.xml')
                        def pom = new XmlParser().parseText(pomFile)
                        def gavMap = [:]
                        env.TAG_VERSION =  pom['version'].text().trim()

                        if (BRANCH_NAME == 'dev' && IS_SIDECAR == 'Y') {
                            K8S_APPLY = K8S_APPLY_SIDECAR
                        }

                        sh 'env'
                    }
                }
            }

            /*   stage('扫码代码') {
                   //failFast true  // 其他阶段失败 中止parallel块同级正在进行的并行阶段
    //               parallel { */// 阶段并发执行
    //        stage('代码质量') {
    //            when {
    //                beforeAgent true
    //                // 生产环境不进行代码分析 缩减构建时间
    //                not {
    //                    anyOf {
    //                        branch 'master'
    //                        branch 'prod'
    //                    }
    //                }
    //                environment name: 'DEPLOY_MODE', value: GlobalVars.release
    //                environment name: 'IS_SONAR', value: 'Y'
    //                expression {
    //                    // 是否进行代码质量分析  && fileExists("sonar-project.properties") == true 代码根目录配置sonar-project.properties文件才进行代码质量分析
    //                    // return ("${IS_CODE_QUALITY_ANALYSIS}" == 'true' )
    //                    return false
    //                }
    //            }
    //            agent {
    //                label "linux"
    //                /*   docker {
    //                       // sonarqube环境  构建完成自动删除容器
    //                       image "sonarqube:community"
    //                       reuseNode true // 使用根节点
    //                   }*/
    //            }
    //            steps {
    //                // 只显示当前阶段stage失败  而整个流水线构建显示成功
    //                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
    //                    script {
    //                        codeQualityAnalysis()
    //                    }
    //                }
    //            }
    //        }



            stage('build') {
//                when {
//                    beforeAgent true
//                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
//                //    expression { return (IS_DOCKER_BUILD == true }
//                }
                steps {
                    container("${map.pipeline_agent_lable}") {
                        script {
                            sh 'echo BUILD_TYPE'
                            switch(BUILD_TYPE){
                                case "mvn":
                                    sh 'echo "mvn"'
//                                    Maven.mvnBuildProject(this)
                                    break
                                case "mvnd":
                                    sh 'echo "mvnd"'
//                                    Maven.mvndBuildProject(this)
                                    break
                                default:
                                    sh 'echo "请选择编译方式，mvnd或其他."'
                                    break
                            }

                        }
                    }
                }
            }

            stage('parallel build modules images') {
//                when {
//                    beforeAgent true
//                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
//                }
                steps {
                    container("${map.pipeline_agent_lable}") {
                        script {
//                            echo 'build modules images'
//                            moduleList = MODULES.split(",").findAll { it }.collect { it.trim() }
//                            def parallelStagesMap = moduleList.collectEntries { key ->
//                                ["build && push  ${key}": generateStage(key)]
//                            }
//                            parallel parallelStagesMap

                            echo 'build modules images'
                            def moduleBuild = [:]
                            moduleList = MODULES.split(",").findAll { it }.collect { it.trim() }
                            imagesList = IMAGES.split(",").findAll { it }.collect { it.trim() }
                            for (key in moduleList) {
                                echo '$key'
                                moduleBuild[key] = {
                                    stage(key) {
                                        container('maven') {
                                            for (imageName in imagesList) {
                                                Docker.pull(this, imageName)
                                            }
                                            Docker.build(this, key)
                                            Docker.push(this, key)
                                        }
                                    }
                                }
                            }
                            parallel moduleBuild
                        }
                    }
                }
                }


            stage('Kubernetes deploy') {
                when {
        //            environment name: 'DEPLOY_MODE', value: GlobalVars.release
        //            beforeAgent true
                    branch 'feature-pipeline-library-v3.1.1'
                }
                steps {
                    script {
                        def moduleDeploy = [:]
                        moduleDeployList = MODULES.split(",").findAll { it }.collect { it.trim() }
                        for (key in moduleDeployList) {
                            moduleDeploy["${key}"] = {
                                stage("${key}") {
                                    container ("${map.pipeline_agent_lable}") {
                                        Kubernetes.deploy(this,key)
                                    }
                                }
                            }
                        }
                        parallel moduleDeploy
                    }
                }
            }
        }
    }
}

/**
 * Maven编译构建
 */


def generateStage(key) {
    return {
        stage('build image ' + key) {
            container('maven') {
                echo 'build   ' + key
                withCredentials([usernamePassword(passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME', credentialsId: "$DOCKER_CREDENTIAL_ID",)]) {
                    sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                    sh 'docker pull ${IMAGE1}'
                    sh 'docker pull ${IMAGE2}'
                }
                sh 'docker build --build-arg REGISTRY=$REGISTRY  --no-cache  -t $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION `pwd`/' + key + '/'
                withCredentials([usernamePassword(passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME', credentialsId: "$DOCKER_CREDENTIAL_ID",)]) {
                    sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                    sh 'docker push  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION'
                    sh 'docker tag  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':latest '
                    sh 'docker push  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':latest '
                }
            }
        }
    }
}

def generateDeploy(key) {
    return {
        stage('deploy ' + key) {
            container ("maven") {
//                withCredentials([usernamePassword(passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME', credentialsId: "$DOCKER_CREDENTIAL_ID",)]) {
//                    sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
//                }
                Kubernetes.deploy(this,key)
//                withCredentials([kubeconfigFile(
//                        credentialsId: env.KUBECONFIG_CREDENTIAL_ID,
//                        variable: 'KUBECONFIG')
//                ]) {
//                    sh 'echo "${IS_SIDECAR}"'
//                    sh 'echo "${K8S_APPLY_SIDECAR}"'
//                    sh 'echo "${K8S_APPLY}"'
//                    sh 'envsubst < ${K8S_APPLY}' + key + '/eip-' + key + '-service.yaml | kubectl apply -f -'
//                    sh 'envsubst < ${K8S_APPLY}' + key + '/eip-' + key + '-deployment.yaml | kubectl apply -f -'
//                }
            }
        }
    }
}

/**
 * 代码质量分析
 */
def codeQualityAnalysis() {

    SonarQube.scan(this, "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}")
    // SonarQube.getStatus(this, "${PROJECT_NAME}")
/*    def scannerHome = tool 'SonarQube' // 工具名称
    withSonarQubeEnv('SonarQubeServer') { // 服务地址链接名称
        // 如果配置了多个全局服务器连接，则可以指定其名称
        sh "${scannerHome}/bin/sonar-scanner"
        // sh "/usr/local/bin/sonar-scanner --version"
    }*/
    // 可打通项目管理平台自动提交bug指派任务
}



/**
 * 云原生K8S部署大规模集群
 */
def k8sDeploy() {
    // 执行部署
    Kubernetes.deploy(this)
}

//def version = "1.2"
//switch(GIT_BRANCH) {
//    case "develop":
//        result = "dev"
//        break
//    case ["master", "support/${version}".toString()]:
//        result = "list"
//        break
//    case "support/${version}":
//        result = "sup"
//        break
//    default:
//        result = "def"
//        break
//}
//echo "${result}"