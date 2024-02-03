package site.zbyte.root

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.internal.FileUtils
import org.gradle.jvm.tasks.Jar

import java.nio.file.Files
import java.util.regex.Pattern

class Builder implements Plugin<Project>{

    static boolean isRelease(Project project){
        project.gradle.startParameter.taskNames
        for(String s : project.gradle.startParameter.taskNames) {
            if (s.contains("Release") | s.contains("release")) {
                return true
            }
        }
        return false
    }

    static String buildType(Project project){
        return isRelease(project)?'release':'debug'
    }

    static String buildTypeUpper(Project project){
        return isRelease(project)?'Release':'Debug'
    }

    static void copyStreamToFile(InputStream input, String path){
        def f=new File(path)
        def fParent=f.parentFile
        if(!fParent.exists())
            fParent.mkdirs()
        def output=new FileOutputStream(f)
        def buf=new byte[1024]
        def size
        while((size=input.read(buf))>0){
            output.write(buf,0,size)
        }
        output.flush()
        output.close()
        input.close()
    }

    ArrayList<String> searchFile(File root,File base,Pattern regex){
        ArrayList<String> list=[]
        base.listFiles().each {
            if(it.isFile()){
                def relative=root.relativePath(it)
                if(regex.matcher(relative).matches()){
                    list.add(relative)
                }
            }else if(it.isDirectory()){
                list.addAll(searchFile(root,it,regex))
            }
        }
        return list
    }

    void copyFile(String base,String regex,String dest){
        Pattern r= Pattern.compile(regex)
        File baseFile=new File(base)
        searchFile(baseFile,baseFile,r).forEach{
            def input=new FileInputStream(baseFile.absolutePath+'/'+it).getChannel()
            def destFile=new File(dest+'/'+it)
            destFile.parentFile.mkdirs()
            destFile.createNewFile()
            def output=new FileOutputStream(destFile).channel
            output.transferFrom(input,0,input.size())
            input.close()
            output.close()
        }
    }

    static void cleanFile(File file){
        if(file.exists()){
            file.delete()
        }
    }

    static class ZRootConf{
        String mainClass=""
        ArrayList<String> localFilter=new ArrayList<>()
        HashMap<String,ArrayList<String>> otherFilter=new HashMap<>()
        String debugModule=null

        void mainClass(String cls){
            mainClass=cls
        }

        void filter(ArrayList<String> list){
            localFilter=list
        }

        void filter(String project,ArrayList<String> list){
            otherFilter[project]=list
        }

        void debug(String project){
            debugModule=project
        }
    }

    @Override
    void apply(Project project) {

        def ext=project.extensions.create('zRoot',ZRootConf)

        String baseFileDir=project.buildDir.path+'/tmp/runner'
        String clsTmpDir=project.buildDir.path+'/tmp/remote/'

        String baseRunnerPath=baseFileDir+'/baseRunner.jar'
        String remoteClassFile=baseFileDir+'/config/site/zbyte/root/Config.class'

        def isWindows=false
        if (System.properties['os.name'].toLowerCase().contains('windows')) {
            isWindows=true
        }

        def localProperties = new File(project.rootDir, "local.properties")
        def properties = new Properties()
        localProperties.withInputStream { instr ->
            properties.load(instr)
        }
        def sdkDir=properties.getProperty('sdk.dir')

        project.afterEvaluate {
            def assetsSrc=project.extensions.getByName('android').getProperties()['sourceSets']['main']['assets']['source']
            assetsSrc.add(project.buildDir.path+'/assets')

            project.tasks.register('copyBaseRunnerFileToBuild') {
                if (ext.debugModule != null) {
                    dependsOn(ext.debugModule + ":syncReleaseLibJars")
                }
                doLast {
                    //写入jar
                    cleanFile(new File(baseRunnerPath))
                    if (ext.debugModule == null) {
                        copyStreamToFile(this.class.getResource('/runner.jar').openStream(), baseRunnerPath)
                    } else {
                        File src = new File(project.rootProject.project(ext.debugModule).buildDir.path + "/intermediates/aar_main_jar/release/classes.jar")
                        def input = new FileInputStream(src)
                        copyStreamToFile(input, baseRunnerPath)
                    }
                    //写入RemoteClass
                    def remoteClassFileObj = new File(remoteClassFile)
                    def contentBytes = "yv66vgAAADQAFQoAAwARBwASBwATAQALUmVtb3RlQ2xhc3MBABJMamF2YS9sYW5nL1N0cmluZzsBAA1Db25zdGFudFZhbHVlCAAUAQAGPGluaXQ+AQADKClWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEAEkxvY2FsVmFyaWFibGVUYWJsZQEABHRoaXMBABhMc2l0ZS96Ynl0ZS9yb290L0NvbmZpZzsBAApTb3VyY2VGaWxlAQALQ29uZmlnLmphdmEMAAgACQEAFnNpdGUvemJ5dGUvcm9vdC9Db25maWcBABBqYXZhL2xhbmcvT2JqZWN0AQABJQAhAAIAAwAAAAEAGQAEAAUAAQAGAAAAAgAHAAEAAQAIAAkAAQAKAAAALwABAAEAAAAFKrcAAbEAAAACAAsAAAAGAAEAAAADAAwAAAAMAAEAAAAFAA0ADgAAAAEADwAAAAIAEA=="
                    def bytes = Base64.getDecoder().decode(contentBytes)
                    int index = -1
                    for (int i = 0; i < bytes.size(); i++) {
                        //搜索%号
                        if (bytes[i] == 0x25) {
                            index = i
                            break
                        }
                    }
                    if (index == -1)
                        throw new Exception("Find % in config file error")

                    def parent = remoteClassFileObj.parentFile
                    if (!parent.exists())
                        parent.mkdirs()
                    def mainClass = ext.mainClass
                    def output = new FileOutputStream(remoteClassFileObj)
                    //写入到index-1位置
                    for (int i = 0; i < index - 1; i++) {
                        output.write(bytes[i])
                    }
                    output.write(mainClass.size())
                    output.write(mainClass.getBytes())
                    for (int i = index + 1; i < bytes.size(); i++) {
                        output.write(bytes[i])
                    }
                    output.flush()
                    output.close()
                }
            }
            project.tasks.register('cleanJar',Delete.class){ del->
                doFirst {
                    del.delete(project.buildDir.path+'/libs')
                }
            }
            project.tasks.register('cleanRemoteClass',Delete.class){ del->
                doFirst {
                    del.delete(clsTmpDir)
                }
            }
            //编译并复制所有需要文件去临时目录
            project.tasks.register('copyRemoteClass'){
                dependsOn('cleanRemoteClass')
                def buildTypeUpper=buildTypeUpper(project)
                //编译本项目
                dependsOn("compile${buildTypeUpper}JavaWithJavac")
                dependsOn("compile${buildTypeUpper}Kotlin")
                //编译三方项目
                ext.otherFilter.keySet().forEach{key->
                    dependsOn("$key:compile${buildTypeUpper}JavaWithJavac")
                    dependsOn("$key:compile${buildTypeUpper}Kotlin")
                }
                doLast {
                    def buildType=buildType(project)
                    //复制编译产物去目录

                    ext.localFilter.forEach{
                        copyFile(project.buildDir.path+'/intermediates/javac/'+buildType+'/classes',it,clsTmpDir)
//                        copyFile(project.buildDir.path+'/tmp/kotlin-classes/'+buildType,it,clsTmpDir)
                    }
                    //复制其他依赖的项目编译产物去目录
                    ext.otherFilter.forEach{key,value->
                        def depProject=project.project(key)
                        value.forEach{
                            copyFile(depProject.buildDir.path+'/intermediates/javac/'+buildType+'/classes',it,clsTmpDir)
//                            copyFile(depProject.buildDir.path+'/tmp/kotlin-classes/'+buildType,it,clsTmpDir)
                        }
                    }
                }
            }
            //打包整个远端类
            project.tasks.register('buildRemoteJar',Jar.class){ jar->
                dependsOn('copyRemoteClass')
                jar.archiveFileName.set("remote.jar")
                doFirst {
                    jar.from(clsTmpDir)
                }
                doLast {
                    jar.into('/')
                }
            }
            project.tasks.register('buildRunnerJar',Jar.class){ jar->
                mustRunAfter('cleanJar')
                dependsOn(['copyBaseRunnerFileToBuild','buildRemoteJar'])
                jar.archiveFileName.set(project.name+'.jar')
                doFirst {
                    jar.manifest{manifest->
                        Map<String,?> attrs=new HashMap<>()
                        attrs['Main-Class']='site.zbyte.root.Runner'
                        manifest.attributes(attrs)
                    }
                    jar.from{
                        project.zipTree(project.buildDir.path+'/libs/remote.jar')
                    }
                    jar.from{
                        project.zipTree(baseRunnerPath).matching{
                            exclude 'site/zbyte/root/Config.class'
                        }
                    }
                    jar.from(baseFileDir+'/config/')
                }
                doLast {
                    jar.into('/')
                }
            }
            project.tasks.register('buildRunnerDex', Exec.class){ exec->
                dependsOn(['cleanJar','buildRunnerJar'])
                doFirst {
                    def buildToolVersion=project.extensions.findByName('android').getProperties()['buildToolsVersion']

                    def d8File=sdkDir+'/build-tools/'+buildToolVersion+'/d8'+(isWindows?'.bat':'')
                    def srcFile=project.buildDir.path+'/libs/'+project.name+'.jar'
                    def outDir=project.buildDir.path+'/libs/'

                    exec.executable(d8File)
                    exec.args(["--release","--output",outDir,srcFile])
                }
            }
            project.tasks.register("copyRunnerDex",Copy.class){
                it.dependsOn('buildRunnerDex')
                from(project.buildDir.path+'/libs/classes.dex')
                into(project.buildDir.path+'/assets/')
                rename("classes.dex","runner.dex")
            }
        }

        project.tasks.configureEach {
            if(it.name=='generateDebugAssets'||it.name=='generateReleaseAssets'){
                it.dependsOn('copyRunnerDex')
            }
        }

    }
}
