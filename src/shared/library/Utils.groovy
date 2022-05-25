#!/usr/bin/env groovy
package shared.library

//import jenkins.model.Jenkins

import java.util.regex.Matcher
import java.util.regex.Pattern
import hudson.model.*;
//@Grab('org.apache.commons:commons-lang3:3.10+')
//import org.apache.commons.lang.time.StopWatch


/**
 * @author 潘维吉
 * @date 2020/10/9 9:20
 * @email 406798106@qq.com
 * @description 工具类*  实现序列化是为了pipeline被jenkins停止重启后能正确恢复
 * 使用引入 import shared.library.Utils
 */
class Utils implements Serializable {
    Utils(script) {
        this.script = script
    }
    static def tagVersion(String TAG_VERSION=''){
        def pomFile = getBuildProperties()
        def pom = new XmlParser().parseText(pomFile)
        def gavMap = [:]
        TAG_VERSION =  pom['version'].text().trim()
        return TAG_VERSION
    }

    def getBuildProperties(){
        return script.readFile('pom.xml')
    }

    static def mavenBuildProject(MODULES) {
        sh 'mvnd -gs ${SETTING_FILE} clean package  -pl ${MODULES}  -am    -Dmaven.test.skip=true -DskipDocker '
        // -Dbuild_env=${ENV_FILE}
    }
}
