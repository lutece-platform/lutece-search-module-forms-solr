package fr.paris.lutece.plugins.forms.modules.solr.service;

import java.util.ArrayList;
import java.util.List;

import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;

public class SolrFormsIndexer implements SolrIndexer
{

	@Override
	public List<Field> getAdditionalFields() {
		return new ArrayList<>();
	}

	@Override
	public String getDescription() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<SolrItem> getDocuments(String arg0) {
		return new ArrayList<>();
	}

	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getResourceUid(String arg0, String arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> getResourcesName() {
		return new ArrayList<>();
	}

	@Override
	public String getVersion() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<String> indexDocuments() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean isEnable() {
		// TODO Auto-generated method stub
		return false;
	}

}
