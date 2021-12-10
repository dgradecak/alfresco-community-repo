/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.alfresco.repo.workflow.activiti;

import java.util.List;
import java.util.stream.Collectors;

import org.activiti.bpmn.model.ActivitiListener;
import org.activiti.bpmn.model.BaseElement;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.delegate.TaskListener;
import org.activiti.engine.impl.bpmn.behavior.MultiInstanceActivityBehavior;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.bpmn.parser.BpmnParse;
import org.activiti.engine.impl.bpmn.parser.handler.AbstractBpmnParseHandler;
import org.activiti.engine.parse.BpmnParseHandler;

/**
 * A {@link BpmnParseHandler} that adds execution listeners to a
 * {@link UserTask} which are specifically for Alfresco usage.
 * 
 * @author Joram Barrez
 * @author Frederik Heremans
 * @author Nick Smith
 */
public class AlfrescoUserTaskBpmnParseHandler extends AbstractBpmnParseHandler<UserTask>
{
    private TaskListener      completeTaskListener;
    private TaskListener      createTaskListener;
    private TaskListener      notificationTaskListener;

    protected Class<? extends BaseElement> getHandledType()
    {
        return UserTask.class;
    }

    protected void executeParse(BpmnParse bpmnParse, UserTask userTask)
    {
        if (userTask.getBehavior() instanceof UserTaskActivityBehavior)
        {
            addListeners(userTask);
        } 
        else if(userTask.getBehavior() instanceof MultiInstanceActivityBehavior)
        {
            MultiInstanceActivityBehavior multiInstance = (MultiInstanceActivityBehavior) userTask.getBehavior();
            if(multiInstance.getInnerActivityBehavior() instanceof UserTaskActivityBehavior) 
            {
                addListeners(userTask);
            }
        }
    }
    
    protected void addListeners(UserTask userTask)
    {
        if (createTaskListener != null)
        {
            addTaskListenerAsFirst(createTaskListener, TaskListener.EVENTNAME_CREATE, userTask);
        }
        if (completeTaskListener != null)
        {
            addTaskListenerAsFirst(completeTaskListener, TaskListener.EVENTNAME_COMPLETE, userTask);
        }
        if(notificationTaskListener != null)
        {
            addTaskListenerAsLast(notificationTaskListener, TaskListener.EVENTNAME_CREATE, userTask);
        }
    }
    
    public void setCompleteTaskListener(TaskListener completeTaskListener)
    {
        this.completeTaskListener = completeTaskListener;
    }

    public void setCreateTaskListener(TaskListener createTaskListener)
    {
        this.createTaskListener = createTaskListener;
    }
    
    public void setNotificationTaskListener(TaskListener notificationTaskListener)
    {
        this.notificationTaskListener = notificationTaskListener;
    }
    
    protected void addTaskListenerAsFirst(TaskListener taskListener, String eventName, UserTask userTask)
    {
        getOrCreateListenerList(eventName, userTask).add(0, taskListener);
    }
    
    protected void addTaskListenerAsLast(TaskListener taskListener, String eventName, UserTask userTask)
    {
        getOrCreateListenerList(eventName, userTask).add(taskListener);
    }
    
    protected List<TaskListener> getOrCreateListenerList(String eventName, UserTask userTask)
    {
        List<TaskListener> taskEventListeners = userTask.getTaskListeners().stream()
                    .filter(listener -> eventName.equals(listener.getEvent()))
                    .map(listener -> (TaskListener) listener.getInstance())
                    .collect(Collectors.toList());

        return taskEventListeners;
    }
}