#!groovy
import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*

/**
 * @author 潘维吉
 * @description 通用核心共享Pipeline脚本库
 * 针对大前端Web和服务端Java、Go、Python、C++等多语言项目
 */
def call(String type = 'web-java', Map map) {
    echo "Pipeline共享库脚本类型: ${type}, jenkins分布式节点名: 前端${map.jenkins_node_front_end} , 后端${map.jenkins_node} "
    // 应用共享方法定义
//    changeLog = new ChangeLog()
//    gitTagLog = new GitTagLog()

    // 初始化参数
//    getInitParams(map)

//    remote = [:]
//    try {
//        remote.host = "${REMOTE_IP}" // 部署应用程序服务器IP 动态参数 可配置在独立的job中
//    } catch (exception) {
//        // println exception.getMessage()
//        remote.host = "${map.remote_ip}" // 部署应用程序服务器IP  不传参数 使用默认值
//    }
//    remote.user = "${map.remote_user_name}"
//    remote_worker_ips = readJSON text: "${map.remote_worker_ips}"  // 分布式部署工作服务器地址 同时支持N个服务器
//    // 代理机或跳板机外网ip用于透传部署到目标机器
//    proxy_jump_ip = "${map.proxy_jump_ip}"


        pipeline {
            // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
            agent {
                node {
                    //label "${PROJECT_TYPE.toInteger() == GlobalVars.frontEnd ? "${map.jenkins_node_front_end}" : "${map.jenkins_node}"}"
                    label 'maven'
                }
            }
            //agent { label "${map.jenkins_node}" }

            parameters {
                choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.rollback],
                        description: '选择部署方式  1. ' + GlobalVars.release + '发布 2. ' + GlobalVars.rollback +
                                '回滚(基于jenkins归档方式回滚选择' + GlobalVars.rollback + ', 基于Git Tag方式回滚请选择' + GlobalVars.release + ')')
   //             gitParameter(name: 'GIT_BRANCH', type: 'PT_BRANCH', defaultValue: "${BRANCH_NAME}", selectedValue: "DEFAULT",
   //                     useRepository: "${REPO_URL}", sortMode: 'ASCENDING', branchFilter: 'origin/(.*)',
   //                     description: "选择要构建的Git分支 默认: " + "${BRANCH_NAME} (可自定义配置具体任务的默认常用分支, 实现一键或全自动构建)")
   //             gitParameter(name: 'GIT_TAG', type: 'PT_TAG', defaultValue: GlobalVars.noGit, selectedValue: GlobalVars.noGit,
   //                     useRepository: "${REPO_URL}", sortMode: 'DESCENDING_SMART', tagFilter: '*',
   //                     description: "DEPLOY_MODE基于" + GlobalVars.release + "部署方式, 可选择指定Git Tag版本标签构建, 默认不选择是获取指定分支下的最新代码, 选择后按tag代码而非分支代码构建⚠️, 同时可作为一键回滚版本使用 🔙 ")
                string(name: 'ROLLBACK_BUILD_ID', defaultValue: '0', description: "DEPLOY_MODE基于" + GlobalVars.rollback + "部署方式, 输入对应保留的回滚构建记录ID, " +
                        "默认0是回滚到上一次连续构建, 当前归档模式的回滚仅适用于在master节点构建的任务")
                booleanParam(name: 'IS_HEALTH_CHECK', defaultValue: "${map.is_health_check}",
                        description: '是否执行服务启动健康检测 否: 可大幅减少构建时间 分布式部署不建议取消')
                booleanParam(name: 'IS_GIT_TAG', defaultValue: "${map.is_git_tag}",
                        description: '是否生产环境自动给Git仓库设置Tag版本和生成CHANGELOG.md变更记录')
                booleanParam(name: 'IS_DING_NOTICE', defaultValue: "${map.is_ding_notice}", description: "是否开启钉钉群通知 📢 ")
                choice(name: 'NOTIFIER_PHONES', choices: "${contactPeoples}", description: '选择要通知的人 (钉钉群内@提醒发布结果) 📢 ')
                //booleanParam(name: 'IS_DEPLOY_MULTI_ENV', defaultValue: false, description: '是否同时部署当前job项目多环境 如dev test等')
            }

            environment {
                // 系统环境变量
                NODE_OPTIONS = "--max_old_space_size=4096" // NODE内存调整 防止打包内存溢出
                // jenkins节点java路径 适配不同版本jdk情况 /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
                //JAVA_HOME = "/var/jenkins_home/tools/hudson.model.JDK/${JDK_VERSION}${JDK_VERSION == '11' ? '/jdk-11' : ''}"
                // 动态设置环境变量  配置相关自定义工具
                //PATH = "${JAVA_HOME}/bin:$PATH"

                NODE_VERSION = "${map.nodejs}" // nodejs版本
                JDK_VERSION = "${map.jdk}" // JDK版本
                CI_GIT_CREDENTIALS_ID = "${map.ci_git_credentials_id}" // CI仓库信任ID
                GIT_CREDENTIALS_ID = "${map.git_credentials_id}" // Git信任ID
                DING_TALK_CREDENTIALS_ID = "${map.ding_talk_credentials_id}" // 钉钉授信ID 系统设置里面配置 自动生成
                DEPLOY_FOLDER = "${map.deploy_folder}" // 服务器上部署所在的文件夹名称
                NPM_PACKAGE_FOLDER = "${map.npm_package_folder}" // Web项目NPM打包代码所在的文件夹名称
                WEB_STRIP_COMPONENTS = "${map.web_strip_components}" // Web项目解压到指定目录层级
                MAVEN_ONE_LEVEL = "${map.maven_one_level}"// 如果Maven模块化存在二级模块目录 设置一级模块目录名称
                DOCKER_JAVA_OPTS = "${map.docker_java_opts}" // JVM内存设置
                DOCKER_MEMORY = "${map.docker_memory}" // docker内存限制
                DOCKER_LOG_OPTS = "${map.docker_log_opts}" // docker日志限制
                IS_PUSH_DOCKER_REPO = "${map.is_push_docker_repo}" // 是否上传镜像到docker容器仓库
                DOCKER_REPO_CREDENTIALS_ID = "${map.docker_repo_credentials_id}" // docker容器镜像仓库账号信任id
                DOCKER_REPO_REGISTRY = "${map.docker_repo_registry}" // docker镜像仓库注册地址
                DOCKER_REPO_NAMESPACE = "${map.docker_repo_namespace}" // docker仓库命名空间名称
                DOCKER_MULTISTAGE_BUILD_IMAGES = "${map.docker_multistage_build_images}" // Dockerfile多阶段构建 镜像名称
                PROJECT_TAG = "${map.project_tag}" // 项目标签或项目简称
                MACHINE_TAG = "1号机" // 部署机器标签
                IS_PROD = "${map.is_prod}" // 是否是生产环境
                IS_SAME_SERVER = "${map.is_same_server}" // 是否在同一台服务器分布式部署
                IS_BEFORE_DEPLOY_NOTICE = "${map.is_before_deploy_notice}" // 是否进行部署前通知
                IS_GRACE_SHUTDOWN = "${map.is_grace_shutdown}" // 是否进行优雅停机
                IS_NEED_SASS = "${map.is_need_sass}" // 是否需要css预处理器sass
                IS_AUTO_TRIGGER = false // 是否是自动触发构建
                IS_GEN_QR_CODE = false // 生成二维码 方便手机端扫描
                IS_ARCHIVE = false // 是否归档
                IS_CODE_QUALITY_ANALYSIS = false // 是否进行代码质量分析的总开关
                IS_INTEGRATION_TESTING = false // 是否进集成测试
                IS_NOTICE_CHANGE_LOG = "${map.is_notice_change_log}" // 是否通知变更记录
            }

            options {
                //失败重试次数
                retry(0)
                //超时时间 job会自动被终止
                timeout(time: 30, unit: 'MINUTES')
                //保持构建的最大个数
    //            buildDiscarder(logRotator(numToKeepStr: "${map.build_num_keep}", artifactNumToKeepStr: "${map.build_num_keep}"))
                //控制台输出增加时间戳
    //            timestamps()
                //不允许同一个job同时执行流水线,可被用来防止同时访问共享资源等
                disableConcurrentBuilds()
                //如果某个stage为unstable状态，则忽略后面的任务，直接退出
                skipStagesAfterUnstable()
                //安静的时期 设置管道的静默时间段（以秒为单位），以覆盖全局默认值
                quietPeriod(3)
                //删除隐式checkout scm语句
    //            skipDefaultCheckout()
                //日志颜色
    //            ansiColor('xterm')
                //当agent为Docker或Dockerfile时, 指定在同一个jenkins节点上,每个stage都分别运行在一个新容器中,而不是同一个容器
                //newContainerPerStage()
            }

            stages {
                stage('初始化') {
                    steps {
                        script {
                            echo '初始化'
                            initInfo()
                            //getShellParams(map)
                        }
                    }
                }

                stage('获取代码') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    /*   tools {
                               git "Default"
                         } */
                    steps {
                        script {
                            echo 'checkout(scm)'
                            checkout(scm)
    //                        pullProjectCode()
    //                        pullCIRepo()
                            /*  parallel( // 步骤内并发执行
                                     'CI/CD代码': {
                                         pullCIRepo()
                                     },
                                     '项目代码': {
                                         pullProjectCode()
                                     })*/
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



                stage('Docker For Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == true && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) }
                    }
                    agent {
                        docker {
                            // JDK MAVEN 环境  构建完成自动删除容器
                            image "maven:${map.maven.replace('Maven', '')}-openjdk-${JDK_VERSION}"
                            args " -v /var/cache/maven/.m2:/root/.m2 "
                            reuseNode true // 使用根节点
                        }
                    }
                    steps {
                        script {
                            mavenBuildProject()
                        }
                    }
                }
                stage('Java构建') {
                    when {
                        beforeAgent true
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression { return (IS_DOCKER_BUILD == false && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) }
                    }
                    tools {
                        // 工具名称必须在Jenkins 管理Jenkins → 全局工具配置中预配置 自动添加到PATH变量中
                        maven "${map.maven}"
                        jdk "${JDK_VERSION}"
                    }
                    steps {
                        script {
                            mavenBuildProject()
                        }
                    }
                }


                stage('制作镜像') {
                    when {
                        beforeAgent true
                        expression { return ("${IS_PUSH_DOCKER_REPO}" == 'true') }
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    }
                    //agent { label "slave-jdk11-prod" }
                    steps {
                        script {
                            buildImage()
                        }
                    }
                }


  //              stage('人工审批') {
  //                  when {
  //                      environment name: 'DEPLOY_MODE', value: GlobalVars.release
  //                      expression {
  //                          return false
  //                      }
  //                  }
  //                  steps {
  //                      script {
  //                          manualApproval()
  //                      }
  //                  }
  //              }



                stage('健康检测') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (params.IS_HEALTH_CHECK == true && IS_BLUE_GREEN_DEPLOY == false)
                        }
                    }
                    steps {
                        script {
                            //healthCheck()
                            echo '健康检查'
                        }
                    }
                }

                stage('集成测试') {
                    when {
                        beforeAgent true
                        // 生产环境不进行集成测试 缩减构建时间
                        not {
                            anyOf {
                                branch 'aaa'
                            }
                        }
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            // 是否进行集成测试  是否存在postman_collection.json文件才进行API集成测试  fileExists("_test/postman/postman_collection.json") == true
                            return ("${IS_INTEGRATION_TESTING}" == 'true' && "${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd
                                    && "${AUTO_TEST_PARAM}" != "" && IS_BLUE_GREEN_DEPLOY == false)
                        }
                    }
/*                    agent {
                        docker {
                            // Node环境  构建完成自动删除容器
                            image "node:${NODE_VERSION}"
                            reuseNode true // 使用根节点
                        }
                    }*/
                    steps {
                        script {
                            //integrationTesting()
                            echo '集成测试'
                        }
                    }
                }

                stage('Kubernetes云原生') {
                    when {
                        environment name: 'DEPLOY_MODE', value: GlobalVars.release
                        expression {
                            return (IS_K8S_DEPLOY == true)  // 是否进行云原生K8S集群部署
                        }
                    }
                    steps {
                        script {
                            // 云原生K8s部署大规模集群
                            k8sDeploy()
                        }
                    }
                }


    //            stage('制品仓库') {
    //                when {
    //                    // branch 'master'
    //                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
    //                    expression {
    //                        return false  // 是否进行制品仓库
    //                    }
    //                }
    //                steps {
    //                    script {
    //                        productsWarehouse(map)
    //                    }
    //                }
    //            }

            }

            // post包含整个pipeline或者stage阶段完成情况
            post() {
                always {
                    script {
                        echo '总是运行，无论成功、失败还是其他状态'
                        alwaysPost()
                    }
                }
                success {
                    script {
                        echo '当前成功时运行'
                        //deployMultiEnv()
                    }
                }
                failure {
                    script {
                        echo '当前失败时才运行'
                        dingNotice(0, "CI/CD流水线失败 ❌")
                    }
                }
                unstable {
                    script {
                        echo '不稳定状态时运行'
                    }
                }
                aborted {
                    script {
                        echo '被终止时运行'
                    }
                }
                changed {
                    script {
                        echo '当前完成状态与上一次完成状态不同执行'
                    }
                }
                fixed {
                    script {
                        echo '上次完成状态为失败或不稳定,当前完成状态为成功时执行'
                    }
                }
                regression {
                    script {
                        echo '上次完成状态为成功,当前完成状态为失败、不稳定或中止时执行'
                    }
                }
            }
        }

    }



/**
 *  获取初始化参数方法
 */
def getInitParams(map) {
    def jsonParams = readJSON text: "${JSON_PARAMS}"
    // println "${jsonParams}"
//    REPO_URL = jsonParams.REPO_URL ? jsonParams.REPO_URL.trim() : "" // Git源码地址
    BRANCH_NAME = jsonParams.BRANCH_NAME ? jsonParams.BRANCH_NAME.trim() : GlobalVars.defaultBranch  // Git默认分支
    PROJECT_TYPE = jsonParams.PROJECT_TYPE ? jsonParams.PROJECT_TYPE.trim() : ""  // 项目类型 1 前端项目 2 后端项目
    // 计算机语言类型 1. Java  2. Go  3. Python  5. C++
    COMPUTER_LANGUAGE = jsonParams.COMPUTER_LANGUAGE ? jsonParams.COMPUTER_LANGUAGE.trim() : "1"
    // 项目名 获取部署资源位置和指定构建模块名等
    PROJECT_NAME = jsonParams.PROJECT_NAME ? jsonParams.PROJECT_NAME.trim() : ""
    SHELL_PARAMS = jsonParams.SHELL_PARAMS ? jsonParams.SHELL_PARAMS.trim() : "" // shell传入前端或后端参数

    // npm包管理工具类型 如:  npm、yarn、pnpm
    NPM_PACKAGE_TYPE = jsonParams.NPM_PACKAGE_TYPE ? jsonParams.NPM_PACKAGE_TYPE.trim() : "npm"
    NPM_RUN_PARAMS = jsonParams.NPM_RUN_PARAMS ? jsonParams.NPM_RUN_PARAMS.trim() : "" // npm run [test]的前端项目参数

    // 是否使用Docker容器环境方式构建打包 false使用宿主机环境
    IS_DOCKER_BUILD = jsonParams.IS_DOCKER_BUILD ? jsonParams.IS_DOCKER_BUILD : true
    IS_BLUE_GREEN_DEPLOY = jsonParams.IS_BLUE_GREEN_DEPLOY ? jsonParams.IS_BLUE_GREEN_DEPLOY : false // 是否蓝绿部署
    IS_ROLL_DEPLOY = jsonParams.IS_ROLL_DEPLOY ? jsonParams.IS_ROLL_DEPLOY : false // 是否滚动部署
    IS_GRAYSCALE_DEPLOY = jsonParams.IS_GRAYSCALE_DEPLOY ? jsonParams.IS_GRAYSCALE_DEPLOY : false // 是否灰度发布
    IS_K8S_DEPLOY = jsonParams.IS_K8S_DEPLOY ? jsonParams.IS_K8S_DEPLOY : false // 是否K8s集群部署
    IS_SERVERLESS_DEPLOY = jsonParams.IS_SERVERLESS_DEPLOY ? jsonParams.IS_SERVERLESS_DEPLOY : false // 是否Serverless发布
    IS_STATIC_RESOURCE = jsonParams.IS_STATIC_RESOURCE ? jsonParams.IS_STATIC_RESOURCE : false // 是否静态web资源
    IS_UPLOAD_OSS = jsonParams.IS_UPLOAD_OSS ? jsonParams.IS_UPLOAD_OSS : false // 是否构建产物上传到OSS
    IS_MONO_REPO = jsonParams.IS_MONO_REPO ? jsonParams.IS_MONO_REPO : false // 是否monorepo单体仓库
    // 是否Maven单模块代码
    IS_MAVEN_SINGLE_MODULE = jsonParams.IS_MAVEN_SINGLE_MODULE ? jsonParams.IS_MAVEN_SINGLE_MODULE : false

    // 设置monorepo单体仓库主包文件夹名
    MONO_REPO_MAIN_PACKAGE = jsonParams.MONO_REPO_MAIN_PACKAGE ? jsonParams.MONO_REPO_MAIN_PACKAGE.trim() : "projects"
    // Maven自定义指定settings.xml文件  如设置私有库或镜像源情况
    MAVEN_SETTING_XML = jsonParams.MAVEN_SETTING_XML ? jsonParams.MAVEN_SETTING_XML.trim() : "${map.maven_setting_xml}".trim()
    AUTO_TEST_PARAM = jsonParams.AUTO_TEST_PARAM ? jsonParams.AUTO_TEST_PARAM.trim() : ""  // 自动化集成测试参数
    // Java框架类型 1. Spring Boot  2. Spring MVC
    JAVA_FRAMEWORK_TYPE = jsonParams.JAVA_FRAMEWORK_TYPE ? jsonParams.JAVA_FRAMEWORK_TYPE.trim() : "1"

    // 默认统一设置项目级别的分支 方便整体控制改变分支 将覆盖单独job内的设置
    if ("${map.default_git_branch}".trim() != "") {
        BRANCH_NAME = "${map.default_git_branch}"
    }
    // 启动时间长的服务是否进行部署前通知  具体job级别设置优先
    if (jsonParams.IS_BEFORE_DEPLOY_NOTICE ? jsonParams.IS_BEFORE_DEPLOY_NOTICE.toBoolean() : false) {
        IS_BEFORE_DEPLOY_NOTICE = true
    }

    // 统一前端monorepo仓库到一个job中, 减少构建依赖缓存大小和jenkins job维护成本
    MONOREPO_PROJECT_NAMES = ""
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd && "${IS_MONO_REPO}" == 'true') {
        MONOREPO_PROJECT_NAMES = PROJECT_NAME.trim().replace(",", "\n")
        def projectNameArray = "${PROJECT_NAME}".split(",") as ArrayList
        def projectNameIndex = projectNameArray.indexOf(params.MONOREPO_PROJECT_NAME)
        PROJECT_NAME = projectNameArray[projectNameIndex]
        SHELL_PARAMS = ("${SHELL_PARAMS}".split(",") as ArrayList)[projectNameIndex]
        NPM_RUN_PARAMS = ("${NPM_RUN_PARAMS}".split(",") as ArrayList)[projectNameIndex]
        if ("${MONO_REPO_MAIN_PACKAGE}".contains(",")) {
            MONO_REPO_MAIN_PACKAGE = ("${MONO_REPO_MAIN_PACKAGE}".split(",") as ArrayList)[projectNameIndex]
        }
        println("大统一前端monorepo仓库项目参数: ${PROJECT_NAME}:${NPM_RUN_PARAMS}:${SHELL_PARAMS}")
    } else {
        MONOREPO_PROJECT_NAMES = GlobalVars.defaultValue
    }

    SHELL_PARAMS_ARRAY = SHELL_PARAMS.split("\\s+")  // 正则表达式\s表示匹配任何空白字符，+表示匹配一次或多次
    SHELL_PROJECT_NAME = SHELL_PARAMS_ARRAY[0] // 项目名称
    SHELL_PROJECT_TYPE = SHELL_PARAMS_ARRAY[1] // 项目类型
    SHELL_HOST_PORT = SHELL_PARAMS_ARRAY[2] // 宿主机对外访问接口
    SHELL_EXPOSE_PORT = SHELL_PARAMS_ARRAY[3] // 容器内暴露端口
    SHELL_ENV_MODE = SHELL_PARAMS_ARRAY[4] // 环境模式 如 dev test prod等

    // 获取通讯录
    contactPeoples = ""
    try {
        def data = libraryResource('contacts.yaml')
        Map contacts = readYaml text: data
        contactPeoples = "${contacts.people}"
    } catch (e) {
        println("获取通讯录失败")
        println(e.getMessage())
    }

    // tag版本变量定义
    tagVersion = ""
    // 是否健康检测失败状态
    isHealthCheckFail = false
    // 扫描二维码地址
    qrCodeOssUrl = ""
    // Java构建包OSS地址Url
    javaOssUrl = ""
    // Web构建包大小
    webPackageSize = ""
    // Java打包类型 jar、war
    javaPackageType = ""
    // Java构建包大小
    javaPackageSize = ""
    // Maven打包后产物的位置
    mavenPackageLocation = ""
}

/**
 * 初始化信息
 */
def initInfo() {
    // 判断平台信息
    if (!isUnix()) {
        error("当前脚本针对Unix(如Linux或MacOS)系统 脚本执行失败 ❌")
    }
    //echo sh(returnStdout: true, script: 'env')
    //sh 'printenv'
    //println "${env.PATH}"
    //println currentBuild
    try {
        echo "$git_event_name"
        IS_AUTO_TRIGGER = true
    } catch (e) {
    }
    // 初始化docker环境变量
    Docker.initEnv(this)

    // 不同语言使用不同的从服务部署脚本
    dockerReleaseWorkerShellName = ""
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Java) {
        dockerReleaseWorkerShellName = "docker-release-worker.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Go) {
        dockerReleaseWorkerShellName = "go/docker-release-worker-go.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Python) {
        dockerReleaseWorkerShellName = "python/docker-release-worker-python.sh"
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd && "${COMPUTER_LANGUAGE}".toInteger() == GlobalVars.Cpp) {
        dockerReleaseWorkerShellName = "cpp/docker-release-worker-cpp.sh"
    }

    // 是否跳板机穿透方式部署
    isProxyJumpType = false
    // 跳板机ssh ProxyJump访问新增的文本
    proxyJumpSSHText = ""
    proxyJumpSCPText = ""
    if ("${proxy_jump_ip}".trim() != "") {
        isProxyJumpType = true
        // ssh -J root@外网跳板机IP:22 root@内网目标机器IP -p 22
        proxyJumpSSHText = " -J root@${proxy_jump_ip} "
        proxyJumpSCPText=" -o 'ProxyJump root@${proxy_jump_ip}' "
    }

}

/**
 * 组装初始化shell参数
 */
def getShellParams(map) {
    if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
        SHELL_WEB_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${DEPLOY_FOLDER} -g ${NPM_PACKAGE_FOLDER} -h ${WEB_STRIP_COMPONENTS} " +
                "-i ${IS_PUSH_DOCKER_REPO}  -k ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE}  "
    } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
        // 使用getopts的方式进行shell参数传递
        SHELL_PARAMS_GETOPTS = " -a ${SHELL_PROJECT_NAME} -b ${SHELL_PROJECT_TYPE} -c ${SHELL_HOST_PORT} " +
                "-d ${SHELL_EXPOSE_PORT} -e ${SHELL_ENV_MODE}  -f ${IS_PROD} -g ${DOCKER_JAVA_OPTS} -h ${DOCKER_MEMORY} " +
                "-i ${DOCKER_LOG_OPTS}  -k ${DEPLOY_FOLDER} -l ${JDK_VERSION} -m ${IS_PUSH_DOCKER_REPO} " +
                "-n ${DOCKER_REPO_REGISTRY}/${DOCKER_REPO_NAMESPACE} -q ${JAVA_FRAMEWORK_TYPE} "
        if ("${map.docker_volume_mount}") {
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -o ${map.docker_volume_mount} "
        }
        if ("${SHELL_PARAMS_ARRAY.length}" == '6') {
            SHELL_REMOTE_DEBUG_PORT = SHELL_PARAMS_ARRAY[5] // 远程调试端口
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -y ${SHELL_REMOTE_DEBUG_PORT}"
        }

        if ("${SHELL_PARAMS_ARRAY.length}" == '7') {
            SHELL_EXTEND_PORT = SHELL_PARAMS_ARRAY[6]  // 扩展端口
            SHELL_PARAMS_GETOPTS = "${SHELL_PARAMS_GETOPTS} -z ${SHELL_EXTEND_PORT}"
        }
        // println "${SHELL_PARAMS_GETOPTS}"
    }
}

/**
 * 获取CI代码库
 */
def pullCIRepo() {
    // 同步部署脚本和配置文件等
    sh ' mkdir -p ci && chmod -R 777 ci'
    dir("${env.WORKSPACE}/ci") {
        def reg = ~/^\*\// // 正则匹配去掉*/字符
        // 根据jenkins配置的scm分支 获取相应分支下脚本和配置 支持多分支构建
        scmBranchName = scm.branches[0].name - reg
        println "Jenkinsfile文件和CI代码库分支: ${scmBranchName}"
        // 拉取Git上的部署文件 无需人工上传
        git url: "${GlobalVars.CI_REPO_URL}", branch: "${scmBranchName}", changelog: false, credentialsId: "${CI_GIT_CREDENTIALS_ID}"
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
 * Maven编译构建
 */
def mavenBuildProject() {
    sh 'mvnd -gs `pwd`/tools/maven/${SETTING_FILE}.xml clean package  -pl ${MODULES}  -am    -Dmaven.test.skip=true -DskipDocker -Dbuild_env=${ENV_FILE}'
}

/**
 * 制作镜像
 * 可通过ssh在不同机器上构建镜像
 */
def buildImage() {
    // 定义镜像唯一构建名称
    dockerBuildImageName = "${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}"
    // Docker多阶段镜像构建处理
    Docker.multiStageBuild(this, "${DOCKER_MULTISTAGE_BUILD_IMAGES}")
    // 构建Docker镜像  只构建一次
    Docker.build(this, "${dockerBuildImageName}")
}

/**
 * 人工卡点审批
 * 每一个人都有点击执行流水线权限  但是不一定有发布上线的权限 为了保证项目稳定安全等需要人工审批
 */
def manualApproval() {
    // 针对生产环境部署前做人工发布审批
    if ("${IS_PROD}" == 'true') {
        // 选择具有审核权限的人员 可以配置一个或多个
        def approvalPersons = ["潘维吉"] // 多审批人数组 参数化配置 也可指定审批人
        def approvalPersonMobiles = ["18863302302"] // 审核人的手机数组 用于钉钉通知等

        // 两种审批 1. 或签(一名审批人员同意或拒绝即可) 2. 会签(须所有审批人同意)
        if ("${approvalPersons}".contains("${BUILD_USER}")) {
            // 如果是有审核权限人员发布的跳过本次审核
        } else {
            // 同时钉钉通知到审核人 点击链接自动进入要审核流水线  如果Jenkins提供Open API审核可直接在钉钉内完成点击审批
            DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "发布流水线申请人工审批 ✍🏻 ",
                    "#### ${BUILD_USER}申请发布${PROJECT_NAME}服务, [请您审批](${env.BUILD_URL}) 👈🏻  !" +
                            " \n ###### Jenkins  [运行日志](${env.BUILD_URL}console)  " +
                            " \n ###### 发布人: ${BUILD_USER}" +
                            " \n ###### 通知时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                    "${approvalPersonMobiles}".split(","))
            input {
                message "请【${approvalPersons.split(",")}】相关人员审批本次部署, 是否同意继续发布 ?"
                ok "同意"
            }
            def currentUser = env.BUILD_USER
            println(currentUser)
            if (!"${approvalPersons}".contains(currentUser)) {
                error("人工审批失败, 您没有审批的权限, 请重新运行流水线发起审批 ❌")
            } else {
                // 审核人同意后通知发布人 消息自动及时高效传递
                DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "您发布流水线已被${currentUser}审批同意 ✅",
                        "#### 前往流水线 [查看](${env.BUILD_URL})  !" +
                                " \n ###### 审批时间: ${Utils.formatDate()} (${Utils.getWeek(this)})",
                        "${BUILD_USER_MOBILE}")
            }
        }
    }
}

/**
 * 健康检测
 */
def healthCheck(params = '') { // 可选参数
    Tools.printColor(this, "开始应用服务健康探测, 请耐心等待... 🚀 ")
    if (params?.trim()) { // 为null或空判断
        // 单机分布式部署从服务
        healthCheckParams = params
    } else {
        healthCheckUrl = "http://${remote.host}:${SHELL_HOST_PORT}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) { // 服务端
            healthCheckUrl = "${healthCheckUrl}/"
        }
        healthCheckParams = " -a ${PROJECT_TYPE} -b ${healthCheckUrl}"
    }
    def healthCheckStart = new Date()
    timeout(time: 10, unit: 'MINUTES') {  // health-check.sh有检测超时时间 timeout为防止shell脚本超时失效兼容处理
        healthCheckMsg = sh(
                script: "ssh  ${proxyJumpSSHText} ${remote.user}@${remote.host} 'cd /${DEPLOY_FOLDER}/ && ./health-check.sh ${healthCheckParams} '",
                returnStdout: true).trim()
    }
    healthCheckTimeDiff = Utils.getTimeDiff(healthCheckStart, new Date()) // 计算启动时间

    if ("${healthCheckMsg}".contains("成功")) {
        Tools.printColor(this, "${healthCheckMsg} ✅")
        dingNotice(1, "**成功 ✅**") // 钉钉成功通知
    } else if ("${healthCheckMsg}".contains("失败")) { // shell返回echo信息包含值
        isHealthCheckFail = true
        Tools.printColor(this, "${healthCheckMsg} ❌", "red")
        println("👉 健康检测失败原因分析: 首选排除CI服务器和应用服务器网络是否连通、应用服务器端口是否开放, 再查看应用服务启动日志是否失败")
        // 钉钉失败通知
        dingNotice(1, "**失败或超时❌** [点击我验证](${healthCheckUrl}) 👈 ", "${BUILD_USER_MOBILE}")
        // 打印应用服务启动失败日志 方便快速排查错误
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志开始 START 👇 ------------", "red")
        sh " ssh ${proxyJumpSSHText} ${remote.user}@${remote.host} 'docker logs ${SHELL_PROJECT_NAME}-${SHELL_PROJECT_TYPE}-${SHELL_ENV_MODE}' "
        Tools.printColor(this, "------------ 应用服务${healthCheckUrl} 启动异常日志结束 END 👆 ------------", "red")
        if ("${IS_ROLL_DEPLOY}" == 'true' || "${IS_BLUE_GREEN_DEPLOY}" == 'true') {
            println '分布式部署情况, 服务启动失败, 自动中止取消job, 防止继续部署导致其他应用服务挂掉 。'
            IS_ROLL_DEPLOY = false
        }
        IS_ARCHIVE = false // 不归档
        currentBuild.result = 'FAILURE' // 失败  不稳定UNSTABLE 取消ABORTED
        error("健康检测失败, 中止当前pipeline运行 ❌")
        return
    }
}

/**
 * 集成测试
 */
def integrationTesting() {
    // 可先动态传入数据库名称部署集成测试应用 启动测试完成销毁 再重新部署业务应用
    try {
        // 创建JMeter性能报告
        Tests.createJMeterReport(this)
        // 创建冒烟测试报告
        Tests.createSmokeReport(this)

        // 结合YApi接口管理做自动化API测试
        def yapiUrl = "http://yapi.panweijikeji.com"
        def testUrl = "${yapiUrl}/api/open/run_auto_test?${AUTO_TEST_PARAM}"
        // 执行接口测试
        def content = HttpRequest.get(this, "${testUrl}")
        def json = readJSON text: "${content}"
        def failedNum = "${json.message.failedNum}"
        def projectId = "${AUTO_TEST_PARAM}".trim().split("&")[2].split("=")[0].replaceAll("env_", "")
        def testCollectionId = "${AUTO_TEST_PARAM}".trim().split("&")[0].replaceAll("id=", "")
        DingTalk.notice(this, "${DING_TALK_CREDENTIALS_ID}", "自动化API集成测试报告 🙋",
                "#### ${json.message.msg} \n #### 测试报告: [查看结果](${testUrl.replace("mode=json", "mode=html")}) 🚨" +
                        "\n ##### 测试总耗时:  ${json.runTime} \n ##### 测试用例不完善也可导致不通过 👉[去完善](${yapiUrl}/project/${projectId}/interface/col/${testCollectionId})  ",
                "${failedNum}" == "0" ? "" : "${BUILD_USER_MOBILE}")
    } catch (e) {
        println "自动化集成测试失败 ❌"
        println e.getMessage()
    }
}



/**
 * 云原生K8S部署大规模集群
 */
def k8sDeploy() {
    // 执行部署
    Kubernetes.deploy(this)
}

/**
 *  Serverless工作流发布  无服务器架构免运维 只需要按照云函数的定义要求进行少量的声明或者配置
 */
def serverlessDeploy() {
    // K8s中的Knative或者结合公有云方案 实现Serverless无服务
}

/**
 * 制品仓库版本管理 如Maven、Npm、Docker等以及通用仓库版本上传 支持大型项目复杂依赖关系
 */
def productsWarehouse(map) {
    //  1. Maven与Gradle仓库  2. Npm仓库  3. Docker镜像仓库  4. 通用OSS仓库

    // Maven与Gradle制品仓库
    // Maven.uploadWarehouse(this)

    // Npm制品仓库
    // Node.uploadWarehouse(this)

    // Docker制品仓库
    // Docker.push(this)

    // 通用OSS制品仓库
    // AliYunOss.upload(this)

}

/**
 * 总会执行统一处理方法
 */
def alwaysPost() {
    // sh 'pwd'
    // cleanWs()  // 清空工作空间
    // Jenkins全局安全配置->标记格式器内设置Safe HTML支持html文本
    try {
        def releaseEnvironment = "${NPM_RUN_PARAMS != "" ? NPM_RUN_PARAMS : SHELL_ENV_MODE}"
        if ("${PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {
            currentBuild.description = "${IS_GEN_QR_CODE == 'true' ? "<img src=${qrCodeOssUrl} width=250 height=250 > <br/> " : ""}" +
                    "<a href='http://${remote.host}:${SHELL_HOST_PORT}'> 👉URL访问地址</a> " +
                    "<br/> 项目: ${PROJECT_NAME}" +
                    "${IS_PROD == 'true' ? "<br/> 版本: ${tagVersion}" : ""} " +
                    "<br/> 大小: ${webPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
        } else if ("${PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            currentBuild.description = "<a href='http://${remote.host}:${SHELL_HOST_PORT}'> 👉API访问地址</a> " +
                    "${javaOssUrl.trim() != '' ? "<br/><a href='${javaOssUrl}'> 👉直接下载构建${javaPackageType}包</a>" : ""}" +
                    "<br/> 项目: ${PROJECT_NAME}" +
                    "${IS_PROD == 'true' ? "<br/> 版本: ${tagVersion}" : ""} " +
                    "<br/> 大小: ${javaPackageSize} <br/> 分支: ${BRANCH_NAME} <br/> 环境: ${releaseEnvironment} <br/> 发布人: ${BUILD_USER}"
        }
    } catch (error) {
        println error.getMessage()
    }
}





