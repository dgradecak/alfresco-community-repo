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

import java.util.List;

import org.alfresco.rest.core.IRestModel;
import org.alfresco.rest.core.assertion.ModelAssertion;
import org.alfresco.utility.model.TestModel;
/**
 * Pojo which represents the search response that includes the highlighting info. 
 * @author Michael Suzuki
 *
 */
public class ResponseHighLightModel extends TestModel implements IRestModel<ResponseHighLightModel>
{
    private ResponseHighLightModel model;
    private String field;
    private List<Object> snippets;
    
    public String getField()
    {
        return field;
    }
    public void setField(String field)
    {
        this.field = field;
    }
    public List<Object> getSnippets()
    {
        return snippets;
    }
    public void setSnippets(List<Object> snippets)
    {
        this.snippets = snippets;
    }
    @Override
    public ModelAssertion<ResponseHighLightModel> and()
    {
        return assertThat();
    }
    @Override
    public ModelAssertion<ResponseHighLightModel> assertThat()
    {
        return new ModelAssertion<ResponseHighLightModel>(this);
    }
    @Override
    public ResponseHighLightModel onModel()
    {
        return model;
    }
    

}
