package com.zongkx.yurss


import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {


    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindowContent = RssToolWindowContent()
        // 正确的方式：从 ApplicationManager 获取 ContentFactory
        val contentFactory = ApplicationManager.getApplication().getService(ContentFactory::class.java)
        val content = contentFactory.createContent(myToolWindowContent.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}