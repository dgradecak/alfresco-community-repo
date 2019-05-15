/*
 * Copyright (C) 2017 Alfresco Software Limited.
 *
 * This file is part of Alfresco
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
 */
package org.alfresco.rest.search;

import org.alfresco.rest.core.RestModels;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Search response sample.
 * {"list": {
 *   "entries": [],
 *   "pagination": {
 *     "maxItems": 100,
 *     "hasMoreItems": false,
 *     "totalItems": 0,
 *     "count": 0,
 *     "skipCount": 0
 *  },
 *    "context": {"consistency": {"lastTxId": 1123}}
 * }}
 **/
public class SearchResponse extends RestModels<SearchNodeModel, SearchNodeModelsCollection>
{
    @JsonProperty(value = "entry")
    SearchResponse model;
    private RestResultSetContextModel context;
    public RestResultSetContextModel getContext()
    {
        return context;
    }
    public void setContext(RestResultSetContextModel context)
    {
        this.context = context;
    }
    
}
