#!/usr/bin/env groovy
package com.cleverbuilder


def getCommitMessage() {
    script.sh(returnStdout: true, script: 'git log -1 --pretty=%B HEAD', label: 'Get Git commit message'
    ).trim()
}
def getCommitId(int len=41) {
    return sh(script: 'git rev-parse HEAD', returnStdout: true).trim()[0..len]
}
def getCommitIdShort(int len=6) {
    return sh(script: 'git rev-parse HEAD', returnStdout: true).trim()[0..len]
}
def createTime() {
    script.sh(returnStdout: true, script: 'date "+%Y-%m-%d %H:%M:%S"', label: 'Get Create Time'
    ).trim()
}
