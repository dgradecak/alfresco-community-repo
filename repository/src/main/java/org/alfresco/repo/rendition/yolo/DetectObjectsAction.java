/*
 * #%L
 * Alfresco Repository
 * %%
 * Copyright (C) 2005 - 2022 Alfresco Software Limited
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
package org.alfresco.repo.rendition.yolo;

import java.util.List;

import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.repo.rendition2.RenditionService2;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.NodeRef;

public class DetectObjectsAction extends ActionExecuterAbstractBase
{
    private final RenditionService2 renditionService2;

    public DetectObjectsAction(RenditionService2 renditionService2)
    {
        this.renditionService2 = renditionService2;
    }

    @Override
    protected void executeImpl(Action action, NodeRef actionedUponNodeRef)
    {
        renditionService2.render(actionedUponNodeRef, "yolodetection");
    }

    @Override
    protected void addParameterDefinitions(List<ParameterDefinition> paramList)
    {
        //do nothing
    }
}
