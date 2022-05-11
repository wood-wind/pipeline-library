#!/usr/bin/env groovy
package com.cleverbuilder

class PublicService {
    String getCommitMessage() {
        script.sh(
            returnStdout: true,
            script: 'git log -1 --pretty=%B HEAD',
            label: 'Get Git commit message'
        ).trim()
    }
    String getCommitId() {
        script.sh(
            returnStdout: true,
            script: 'git rev-parse  HEAD',
            label: 'Get Git commit ID'
        ).trim()
    }
    String getCommitIdShort() {
        script.sh(
            returnStdout: true,
            script: 'git log --oneline -1 | awk '{print $1}'",
            label: 'Get Git commit ID short'
        ).trim()
    }
    String createTime() {
        script.sh(
            returnStdout: true,
            script: 'date "+%Y-%m-%d %H:%M:%S"',
            label: 'Get Create Time'
        ).trim()
    }
}