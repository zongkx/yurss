package com.zongkx.yurss


import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * 集中管理插件所有图标的单例对象
 */
object MyIcons {
    @JvmStatic
    val ADD: Icon = IconLoader.getIcon("/add.svg", MyIcons::class.java)

    @JvmStatic
    val DELETE: Icon = IconLoader.getIcon("/delete.svg", MyIcons::class.java)
}