package shared.library.common

import shared.library.GlobalVars

/**
 * @author 潘维吉
 * @date 2021/3/18 16:29
 * @email 406798106@qq.com
 * @description  Docker相关
 * 构建Docker镜像与上传远程仓库 拉取镜像 等
 */
class Docker implements Serializable {

    // 镜像标签  也可自定义版本标签用于无需重复构建相同的镜像, 做到复用镜像CD持续部署到多环境中
    static def imageTag = "latest"


    /**
     *  构建Docker镜像
     */
    static def build(ctx, key) {
        def imageFullName = "${ctx.REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${key}"
        ctx.sh "docker build --build-arg REGISTRY=${ctx.REGISTRY}  --no-cache  -t ${imageFullName}:${ctx.TAG_VERSION} ./${key}/"
        return imageFullName
    }

    /**
     *  Docker镜像上传远程仓库
     */
    static def push(ctx, key) {
        def imageFullName = "${ctx.REGISTRY}/${ctx.DOCKER_REPO_NAMESPACE}/${key}"
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_CREDENTIAL_ID}", usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
            ctx.sh "echo ${ctx.DOCKER_PASSWORD} | docker login ${ctx.REGISTRY} -u ${ctx.DOCKER_USERNAME} --password-stdin"
            ctx.sh "docker push ${imageFullName}:${ctx.TAG_VERSION}"
            ctx.sh "docker tag ${imageFullName}:${ctx.TAG_VERSION}  ${imageFullName}:latest"
            ctx.sh "docker push ${imageFullName}:latest"

        }
    }

    /**
     *  拉取远程仓库Docker镜像
     */
    static def pull(ctx,imageName) {
        ctx.withCredentials([ctx.usernamePassword(credentialsId: "${ctx.DOCKER_CREDENTIAL_ID}", usernameVariable: 'DOCKER_USERNAME', passwordVariable: 'DOCKER_PASSWORD')]) {
            ctx.sh "echo ${ctx.DOCKER_PASSWORD} | docker login ${ctx.REGISTRY} -u ${ctx.DOCKER_USERNAME} --password-stdin"
            ctx.sh "docker pull ${imageName}"
        }
    }

    /**
     *  Docker多阶段镜像构建处理
     */
    static def multiStageBuild(ctx, imageName) {
        if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.frontEnd) {

        } else if ("${ctx.PROJECT_TYPE}".toInteger() == GlobalVars.backEnd) {
            if ("${imageName}".trim() != "") {
                ctx.println("Docker多阶段镜像构建镜像名称: " + imageName)
                def dockerFile = "${ctx.env.WORKSPACE}/ci/.ci/Dockerfile"
                def dockerFileContent = ctx.readFile(file: "${dockerFile}")
                ctx.writeFile file: "${dockerFile}", text: "${dockerFileContent}"
                        .replaceAll("#FROM-MULTISTAGE-BUILD-IMAGES", "FROM ${imageName}")
                        .replaceAll("#COPY-MULTISTAGE-BUILD-IMAGES", "COPY --from=0 / /")
            }
        }
    }

}
