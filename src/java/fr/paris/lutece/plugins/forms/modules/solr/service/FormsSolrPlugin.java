package fr.paris.lutece.plugins.forms.modules.solr.service;

import java.util.ArrayList;
import java.util.List;

import fr.paris.lutece.plugins.forms.business.FormHome;
import fr.paris.lutece.plugins.forms.business.FormResponse;
import fr.paris.lutece.portal.service.plugin.Plugin;
import fr.paris.lutece.portal.service.plugin.PluginDefaultImplementation;
import fr.paris.lutece.portal.service.plugin.PluginService;

public class FormsSolrPlugin extends PluginDefaultImplementation {

	/** The Constant PLUGIN_NAME. */
    public static final String PLUGIN_NAME = "forms-solr";
   /**
     * Gets the plugin.
     *
     * @return the plugin
     */
    public static Plugin getPlugin(  )
    {
        return PluginService.getPlugin( PLUGIN_NAME );
    }
    @Override
    public void init( )
    {
        super.init( );
        List<String> list = new ArrayList<>( );
        list.add( FormResponse.RESOURCE_TYPE );
        FormHome.getFormsReferenceList().forEach(
        				item -> list.add( FormResponse.RESOURCE_TYPE+"_" + item.getCode( ) ));
        				SolrFormsIndexer.initListResourceName( list );
    }
}
