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

    static def getShEchoResult(ctx, cmd) {
        def getShEchoResultCmd = "ECHO_RESULT=`${cmd}`\necho \${ECHO_RESULT}"
        return ctx.sh(
                script: getShEchoResultCmd,
                returnStdout: true,
                encoding: 'UTF-8'
        ).trim()
    }
}
