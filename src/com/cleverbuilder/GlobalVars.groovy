#!/usr/bin/env groovy
package com.cleverbuilder

class GlobalVars {
   static String COMMIT_ID_SHORT = sh(returnStdout: true, script: 'git log --oneline -1 | awk \'{print \$1}\'')
   static String COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse  HEAD')
   static String CREATE_TIME = sh(returnStdout: true, script: 'date "+%Y-%m-%d %H:%M:%S"')

   // refer to this in a pipeline using:
   //
   // import com.cleverbuilder.GlobalVars
   // println GlobalVars.foo
}
