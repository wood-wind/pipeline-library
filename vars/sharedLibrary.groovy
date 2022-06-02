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
            SETTING_FILE = "${map.setting_file}"
            KUBECONFIG_CREDENTIAL_ID = "${map.kubeconfig_credential_id}"
            NACOS_SVC = "${map.nacos_svc}"
            NACOS_NAMESPACE = "${map.nacos_namespace}"
            FILE_UPLOAD = "${map.file_upload}"
            IMAGES = "${map.images}"                                            // 需要额外拉取的镜像
            K8S_APPLY = "${map.k8s_apply}"
            K8S_APPLY_SIDECAR = "${map.k8s_apply_sidecar}"
            IS_SIDECAR = "${map.is_sidecar}"
            SKYWALKING_COLLECTOR_BACKEND_SERVICES = "${map.skywalking_collector_backend_services}"
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
            //skipDefaultCheckout()
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
                    container("${map.pipeline_agent_lable}") {
                        script {
                            def pomFile = readFile(file: 'pom.xml')
                            def pom = new XmlParser().parseText(pomFile)
                            def gavMap = [:]
                            env.TAG_VERSION = pom['version'].text().trim()
                            sh "echo 111111"
                            sh "echo ${K8S_APPLY_SIDECAR}"
                            sh "echo ${BRANCH_NAME}"
                            if (BRANCH_NAME == 'pipeline-shared-dev' && IS_SIDECAR == 'Y') {
                                env.K8S_APPLY = env.K8S_APPLY_SIDECAR
                                sh "echo ${K8S_APPLY}"
                            }
                            sh 'env'
                        }
                    }
                }
            }

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
                                    Maven.mvnBuildProject(this)
                                    break
                                case "mvnd":
                                    sh 'echo "mvnd"'
                                    Maven.mvndBuildProject(this)
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
                steps {
                    container("${map.pipeline_agent_lable}") {
                        script {
                            def moduleBuild = [:]
                            moduleList = MODULES.split(",").findAll { it }.collect { it.trim() }
                            imagesList = IMAGES.split(",").findAll { it }.collect { it.trim() }
                            for (int i = 0; i < moduleList.size(); i++) {
                                def key = moduleList[i]
                                moduleBuild[key] = {
                                    stage(key) {
                                        for (imageName in imagesList) {
                                            Docker.pull(this, imageName)
                                        }
                                        Docker.build(this, key)
                                        Docker.push(this, key)
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
                    branch 'pipeline-shared-dev'
                }
                steps {
                    container("${map.pipeline_agent_lable}") {
                        script {
                            def moduleDeploy = [:]
                            moduleDeployList = MODULES.split(",").findAll { it }.collect { it.trim() }
                            for (int i = 0; i < moduleDeployList.size(); i++) {
                                def key = moduleDeployList[i]
                                moduleDeploy[key] = {
                                    stage(key) {
                                        Kubernetes.deploy(this, key)
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
}
