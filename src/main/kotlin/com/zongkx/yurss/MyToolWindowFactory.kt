package com.zongkx.yurss


import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class MyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindowContent = RssToolWindowContent(project)
        val contentFactory = project.getService(ContentFactory::class.java)
        val content = contentFactory.createContent(myToolWindowContent.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }
}