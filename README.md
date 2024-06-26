# ZRoot-builder
[![](https://jitpack.io/v/site.zbyte/zroot-builder.svg)](https://jitpack.io/#site.zbyte/zroot-builder)

ZRoot-builder is a extension of [ZRoot](https://github.com/gogogoghost/ZRoot) for running custom code(service) on remote root process.

## Usage

Add it in your root build.gradle at the end of repositories:

```css
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
}
```

Add classpath into root build.gradle

```groovy
dependencies{
    classpath "site.zbyte:zroot-builder:${version}"
}
```

Add plugin in the plugins block of app's build.gradle

```groovy
plugins{
    id 'zroot-builder'
}
```

Add Config at the blank place of build.gradle

```groovy
zRoot{
    /**
     * which service class you need run on remote process.
     * it must be a binder.
     */
    mainClass("site.zbyte.root.app.remote.Worker")
    /**
     * which classes you need to pack into remote process.
     * for example, dependencies of main class.
     * it's a regex string array.
     * these classes will be packed into a jar and compile to dex.
     * if you are using kotlin, recommend you keep these classes sources written in java.
     */
    filter([
            "site/zbyte/root/app/remote/.*",
            "site/zbyte/root/app/IWorker.*"
    ])
    /**
     * you can also include classes from other module of current project.
     */
    filter(
            ":other",
            [
                    "site/zbyte/root/other/.*"
            ]
    )
}
```

Make sure that **sourceSets** contains the generated dex file

```groovy
sourceSets {
    main {
        assets.srcDirs = [buildDir.path+'/assets']
    }
}
```

Make sure the filter includes all classes the main class needs.

Remote classes currently only supports **JAVA language.**

Get the instance by ZRoot: 

```kotlin
ZRoot.Starter(context).start(5000) {zRoot->
    if (zRoot==null)
        return@start

    /**
     * get custom remote worker
     */
    val worker = IWorker.Stub.asInterface(zRoot.getWorker())
    /**
     * invoke remote method
     */
    println(worker.work())
}
```