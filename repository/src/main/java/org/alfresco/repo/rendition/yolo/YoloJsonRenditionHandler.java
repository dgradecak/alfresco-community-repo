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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.alfresco.repo.rendition2.RenditionHandler;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.tagging.TaggingService;
import org.alfresco.util.collections.JsonUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;

public class YoloJsonRenditionHandler implements RenditionHandler
{
    private final TaggingService taggingService;

    public YoloJsonRenditionHandler(TaggingService taggingService)
    {
        this.taggingService = taggingService;
    }

    @Override
    public void handle(NodeRef sourceNodeRef, InputStream transformInputStream)
    {
        try (Reader reader = new BufferedReader(new InputStreamReader(transformInputStream)))
        {
            List<String> detectedObjects = JsonUtils.toListOfStrings(new JSONArray(IOUtils.toString(reader)));
            taggingService.setTags(sourceNodeRef, detectedObjects);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
