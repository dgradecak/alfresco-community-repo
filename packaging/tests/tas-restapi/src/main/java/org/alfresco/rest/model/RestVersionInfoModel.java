package org.alfresco.rest.model;

import org.alfresco.rest.core.IRestModel;
import org.alfresco.rest.core.assertion.ModelAssertion;
import org.alfresco.utility.model.TestModel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Generated by 'aforascu' on '2018-01-10 16:02' from 'Alfresco Content Services REST API' swagger file 
 * Generated from 'Alfresco Content Services REST API' swagger file
 * Base Path {@linkplain /alfresco/api}
 */
public class RestVersionInfoModel extends TestModel implements IRestModel<RestVersionInfoModel>
{
    @Override
    public ModelAssertion<RestVersionInfoModel> assertThat()
    {
        return new ModelAssertion<RestVersionInfoModel>(this);
    }

    @Override
    public ModelAssertion<RestVersionInfoModel> and()
    {
        return assertThat();
    }

    @JsonProperty(value = "entry")
    RestVersionInfoModel model;

    @Override
    public RestVersionInfoModel onModel()
    {
        return model;
    }


    @JsonProperty(required = true)    
    private String major;	    

    @JsonProperty(required = true)    
    private String minor;	    

    @JsonProperty(required = true)    
    private String patch;	    

    @JsonProperty(required = true)    
    private String hotfix;	    

    @JsonProperty(required = true)    
    private int schema;	    

    @JsonProperty(required = true)    
    private String label;	    

    @JsonProperty(required = true)    
    private String display;	    

    public String getMajor()
    {
        return this.major;
    }

    public void setMajor(String major)
    {
        this.major = major;
    }				

    public String getMinor()
    {
        return this.minor;
    }

    public void setMinor(String minor)
    {
        this.minor = minor;
    }				

    public String getPatch()
    {
        return this.patch;
    }

    public void setPatch(String patch)
    {
        this.patch = patch;
    }				

    public String getHotfix()
    {
        return this.hotfix;
    }

    public void setHotfix(String hotfix)
    {
        this.hotfix = hotfix;
    }				

    public int getSchema()
    {
        return this.schema;
    }

    public void setSchema(int schema)
    {
        this.schema = schema;
    }				

    public String getLabel()
    {
        return this.label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }				

    public String getDisplay()
    {
        return this.display;
    }

    public void setDisplay(String display)
    {
        this.display = display;
    }				
}
 
