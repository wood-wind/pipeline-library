def call(Map config=[:]) {
    def pomFile = readFile(file: 'pom.xml')
    def pom = new XmlParser().parseText(pomFile)
    def gavMap = [:]
    return TAG_VERSION =  pom['version'].text().trim()
}