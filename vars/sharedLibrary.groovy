#!groovy

import shared.library.GlobalVars
import shared.library.Utils
import shared.library.common.*


def call(Map map) {
    echo "开始构建，进入主方法..."
    pipeline {
        // 指定流水线每个阶段在哪里执行(物理机、虚拟机、Docker容器) agent any
        agent {
            node {
                label "${map.pipeline_agent_lable}"
                //label 'maven'
            }
        }
        parameters {
            choice(name: 'DEPLOY_MODE', choices: [GlobalVars.release, GlobalVars.dev],description: '选择部署方式  1.release 2.dev分支')
            choice(name: 'ENV_FILE', choices: ['halosee','cs','cs-master','crrc','halosee-new'], description: '环境变量')
            choice(name: 'IS_DEPLOY', choices: ['Y',''], description: '是否部署,Y或置空')

        }

        environment {
            DOCKER_REPO_CREDENTIALS_ID = "${map.docker_repo_credentials_id}" // docker容器镜像仓库账号信任id
            REGISTRY = "${map.registry}" // docker镜像仓库注册地址
            DOCKER_REPO_NAMESPACE = "${map.docker_repo_namespace}" // docker仓库命名空间名称
            PIPELINE_AGENT_LABLE = "${map.pipeline_agent_lable}"

            //DEPLOY_FOLDER = "${map.deploy_folder}" // 服务器上部署所在的文件夹名称

            //IS_CODE_QUALITY_ANALYSIS = false // 是否进行代码质量分析的总开关
            SETTING_FILE="${map.setting_file}"

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

            stage("fetch pom version") {
                steps {
                    script {
//                        def pomFile = readFile(file: 'pom.xml')
//                        def pom = new XmlParser().parseText(pomFile)
//                        def gavMap = [:]
//                        env.TAG_VERSION =  pom['version'].text().trim()
//                        sh 'env'
                         env.TAG_VERSION =  Utils.get_TAG_VERSION()
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
                when {
                    beforeAgent true
                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
                //    expression { return (IS_DOCKER_BUILD == true }
                }
                steps {
                    container('maven') {
                        script {
                            sh 'echo "build"'
                            mavenBuildProject(MODULES)
                        }
                    }
                }
            }

            stage('parallel build modules images') {
                when {
                    beforeAgent true
                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
                }
                steps {
                    script {
                            echo 'build modules images'
                            moduleList = MODULES.split(",").findAll { it }.collect { it.trim() }
                            def parallelStagesMap = moduleList.collectEntries { key ->
                                ["build && push  ${key}": generateStage(key)]
                            }
                            parallel parallelStagesMap
                        }
                    }
                }

//            stage('健康检测') {
//                when {
//                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
//                    expression {
//                        return (params.IS_HEALTH_CHECK == true && IS_BLUE_GREEN_DEPLOY == false)
//                    }
//                }
//                steps {
//                    script {
//                        //healthCheck()
//                        echo '健康检查'
//                    }
//                }
//            }

            stage('Kubernetes云原生') {
                when {
                    environment name: 'DEPLOY_MODE', value: GlobalVars.release
                    expression {
                        return (IS_DEPLOY == true)  // 是否进行云原生K8S集群部署
                    }
                }
                steps {
                    script {
                        k8sDeploy()
                    }
                }
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
 * Maven编译构建
 */
def mavenBuildProject(MODULES) {
    sh 'mvnd -gs `pwd`/tools/maven/${SETTING_FILE}.xml clean package  -pl ${MODULES}  -am    -Dmaven.test.skip=true -DskipDocker -Dbuild_env=${ENV_FILE}'
}

//def parallelStagesMap = MODULES.collectEntries { key, value ->
//    ["build && push  ${key}": generateStage(key, value)]
//}


def generateStage(key) {
    return {
        stage('build image ' + key) {
            container('maven') {
                echo 'build   ' + key
                withCredentials([usernamePassword(passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME', credentialsId: "$DOCKER_REPO_CREDENTIALS_ID",)]) {
                    sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                    sh 'docker pull ${REGISTRY}/halosee/nginx:stable-alpine'
                    sh 'docker pull ${REGISTRY}/halosee/node:12-alpine'
                }
                sh 'docker build --build-arg REGISTRY=$REGISTRY  --no-cache  -t $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION `pwd`/' + key + '/'
                withCredentials([usernamePassword(passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME', credentialsId: "$DOCKER_REPO_CREDENTIALS_ID",)]) {
                    sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
                    sh 'docker push  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION'
                    sh 'docker tag  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':$TAG_VERSION $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':latest '
                    sh 'docker push  $REGISTRY/$DOCKER_REPO_NAMESPACE/' + key + ':latest '
                }
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