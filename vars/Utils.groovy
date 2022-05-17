import hudson.model.*;

def get_TAG_VERSION() {
    def pomFile = readFile(file: 'pom.xml')
    echo '${pomFile}'
    def pom = new XmlParser().parseText(pomFile)
    def gavMap = [:]
    TAG_VERSION =  pom['version'].text().trim()
    return TAG_VERSION
}

//def isdir(String d) {
//    def directory = new File(d)
//    if (!directory.exists() || ! directory.isDirectory()) {
//        log.err directory + ' does not exist or is not a directory.'
//    }
//}
//
//def permission(String u) {
//    log.a 'Initiating permission check for build.'
//
//    def private deny = true
//    def private user = u.split('@')[0]
//    def private data = metis.getGitRepoInfo(Config.data.git_repo_id, '/users')
//
//    try{
//        if(user in ['rulin', 'admin']){
//            log.i 'System Administrators is always allowed.'
//            return
//        }
//
//        if(user in data['username']){
//            log.i 'Permission OK, start pipeline. '
//            return
//        }
//        else {
//            def errs = ', you do not have permission to build this job. '
//            log.err 'Dear ' + Config.data.build_user + errs + 'A notification has been sent to Admins.'
//        }
//    }
//    catch(e) {
//        log.e 'Error occurred during permission check.'
//        throw e
//    }
//}

//return this