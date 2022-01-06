/*
 * Copyright (c) 2002-2021, City of Paris
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice
 *     and the following disclaimer.
 *
 *  2. Redistributions in binary form must reproduce the above copyright notice
 *     and the following disclaimer in the documentation and/or other materials
 *     provided with the distribution.
 *
 *  3. Neither the name of 'Mairie de Paris' nor 'Lutece' nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * License 1.0
 */
package fr.paris.lutece.plugins.forms.modules.solr.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import fr.paris.lutece.plugins.forms.business.Form;
import fr.paris.lutece.plugins.forms.business.FormHome;
import fr.paris.lutece.plugins.forms.business.FormQuestionResponse;
import fr.paris.lutece.plugins.forms.business.FormQuestionResponseHome;
import fr.paris.lutece.plugins.forms.business.FormResponse;
import fr.paris.lutece.plugins.forms.business.FormResponseHome;
import fr.paris.lutece.plugins.forms.business.Question;
import fr.paris.lutece.plugins.forms.business.QuestionHome;
import fr.paris.lutece.plugins.forms.business.form.search.FormResponseSearchItem;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeDate;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeGeolocation;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeNumbering;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.leaflet.business.GeolocItem;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.service.state.IStateService;
import fr.paris.lutece.portal.service.search.IndexationService;
import fr.paris.lutece.portal.service.search.SearchIndexer;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

public class SolrFormsIndexer implements SolrIndexer
{
    private static final int TAILLE_LOT = AppPropertiesService.getPropertyInt( "forms-solr.index.writer.commit.size", 100 );


    @Autowired( required = false )
    private IStateService _stateService;

    @Override
    public List<Field> getAdditionalFields( )
    {
        return null;
    }

    @Override
    public String getDescription( )
    {
        return Utilities.FORMS_DESCRIPTION;
    }

    @Override
    public List<SolrItem> getDocuments( String idFormResponse )
    {
    	List<SolrItem> solrItemList = new ArrayList< >( );
    	Map<Integer, Form> mapForms = FormHome.getFormList( ).stream( ).collect( Collectors.toMap( Form::getId, form -> form ) );
		
    	int idDocument = Integer.parseInt( idFormResponse );
		FormResponse response = FormResponseHome.findByPrimaryKeyForIndex( idDocument );
		List<FormQuestionResponse> formQuestionResponseList = FormQuestionResponseHome.getFormQuestionResponseListByFormResponse(idDocument);
		Form form = mapForms.get( response.getFormId( ) );
		State formResponseState = null;
        if ( _stateService != null )
        {
            formResponseState = _stateService.findByResource( response.getId( ), FormResponse.RESOURCE_TYPE, form.getIdWorkflow( ) );
        }
        else
        {
            formResponseState = new State( );
            formResponseState.setId( -1 );
            formResponseState.setName( StringUtils.EMPTY );
        }
		SolrItem solrItem = null;
		try
        {
            solrItem = getSolrItem( response, form, formResponseState, formQuestionResponseList );
        }
        catch( Exception e )
        {
            IndexationService.error( (SearchIndexer) this, e, null );
        }
		if (solrItem != null)
		{
			solrItemList.add(solrItem);
		}
        return solrItemList;
    }

    @Override
    public String getName( )
    {
        return Utilities.FORMS_NAME;
    }

    @Override
    public String getResourceUid( String strResourceId, String strResourceType )
    {
        StringBuilder stringBuilder = new StringBuilder( strResourceId );
        if ( Utilities.RESOURCE_TYPE_FORMS.equals( strResourceType ) )
        {
            stringBuilder.append( '_' ).append( Utilities.SHORT_NAME_FORMS );
        }
        else
        {
            AppLogService.error( "SolrAppointmentIndexer, unknown resourceType: {}", strResourceType );
            return null;
        }
        return stringBuilder.toString( );
    }

    @Override
    public List<String> getResourcesName( )
    {
    	List<String> list = new ArrayList<>( );
    	list.add( FormResponse.RESOURCE_TYPE );
        return list;
    }

    @Override
    public String getVersion( )
    {
        return Utilities.FORMS_VERSION;
    }

    @Override
    public List<String> indexDocuments( )
    {
        List<String> errors = new ArrayList<>();
        List<Integer> listFormResponsesId = FormResponseHome.selectAllFormResponsesId( );

        try
            {
                List<FormResponse> formResponsesListLot = new ArrayList<>( TAILLE_LOT );
                List<FormResponse> formResponseList = FormResponseHome.getFormResponseUncompleteByPrimaryKeyList(listFormResponsesId);
                List<FormQuestionResponse> listFormQuestionResponse = FormQuestionResponseHome.getFormQuestionResponseListByFormResponseList( listFormResponsesId );
                for (FormResponse response : formResponseList)
            	{
            		if ( response != null )
                    {
                    	formResponsesListLot.add( response );
                    }
                    if ( formResponsesListLot.size( ) == TAILLE_LOT )
                    {
                        indexFormResponseList( formResponsesListLot, listFormQuestionResponse );
                        formResponsesListLot.clear( );
                    }
            	}
                indexFormResponseList( formResponsesListLot, listFormQuestionResponse );
            }
            catch( Exception e )
            {
                AppLogService.error( e.getMessage( ), e );
                errors.add(e.toString());
            }
        return errors;
    }

    @Override
    public boolean isEnable( )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( Utilities.PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * {@inheritDoc}
     * @param listFormResponse 
     * @param listFormQuestionResponse 
     */
    private void indexFormResponseList( List<FormResponse> listFormResponse, List<FormQuestionResponse> listFormQuestionResponse )
    {
        Map<Integer, Form> mapForms = FormHome.getFormList( ).stream( ).collect( Collectors.toMap( Form::getId, form -> form ) );
        Collection<SolrItem> solrItemList = new ArrayList<>( );
        for ( FormResponse formResponse : listFormResponse )
        {
            SolrItem solrItem = null;
            Form form = mapForms.get( formResponse.getFormId( ) );
            State formResponseState = null;
            
            if ( _stateService != null )
            {
                formResponseState = _stateService.findByResource( formResponse.getId( ), FormResponse.RESOURCE_TYPE, form.getIdWorkflow( ) );
            }
            else
            {
                formResponseState = new State( );
                formResponseState.setId( -1 );
                formResponseState.setName( StringUtils.EMPTY );
            }

            try
            {
                solrItem = getSolrItem( formResponse, form, formResponseState, listFormQuestionResponse );
            }
            catch( Exception e )
            {
            	AppLogService.error("Error during indexation", e);
            }

            if ( solrItem != null )
            {
                solrItemList.add( solrItem );
            }
        }
        addSolrItems( solrItemList );
    }

    private void addSolrItems( Collection<SolrItem> solrItemList )
    {
        try
        {
        	SolrIndexerService.write( solrItemList );
        }
        catch( IOException e )
        {
            AppLogService.error( "Unable to index form response", e );
        }
        solrItemList.clear( );
    }

    private SolrItem getSolrItem( FormResponse formResponse, Form form, State formResponseState, List<FormQuestionResponse> listFormQuestionResponseGlobal )
    {
    	List<FormQuestionResponse> formQuestionResponseList = listFormQuestionResponseGlobal.stream().filter(x -> x.getIdFormResponse( ) == formResponse.getId( )).collect( Collectors.toList( ) );
        SolrItem solrItem = initSolrItem(formResponse, form, formResponseState, formQuestionResponseList);
        // --- form response entry code / fields
        for ( FormQuestionResponse formQuestionResponse : formQuestionResponseList )
        {
            for ( Response response : formQuestionResponse.getEntryResponse( ) )
            {
                indexResponse(solrItem, formQuestionResponse, response, formResponse.getId( ));
            }
        }
        return solrItem;
    }
    
    private SolrItem initSolrItem( FormResponse formResponse, Form form, State formResponseState, List<FormQuestionResponse> formQuestionResponseList )
    {
    	// make a new, empty SolrItem
        SolrItem solrItem = new SolrItem( );

        int nIdFormResponse = formResponse.getId( );

        solrItem.setSite( SolrIndexerService.getWebAppName( ) );
        solrItem.setRole( Utilities.SHORT_ROLE_FORMS );
        solrItem.setType( Utilities.SHORT_NAME_FORMS );
        solrItem.setUid( String.valueOf( nIdFormResponse ) + '_' + Utilities.SHORT_ROLE_FORMS );
        solrItem.setTitle( Utilities.SHORT_ROLE_FORMS + " #" + nIdFormResponse );
        solrItem.setDate( formResponse.getCreation( ) );
        solrItem.setUrl("#");

        // --- form response identifier
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, String.valueOf( nIdFormResponse ) );
        
        // --- field contents
        solrItem.setContent(manageNullValue( getContentToIndex( formQuestionResponseList, form ) ));

        // --- form title
        String strFormTitle = manageNullValue( form.getTitle( ) );
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_FORM_TITLE, strFormTitle );

        // --- id form
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_FORM, String.valueOf( form.getId( ) ) );

        // --- form response date create
        Long longCreationDate = formResponse.getCreation( ).getTime( );
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_DATE_CREATION, longCreationDate );

        // --- form response date closure
        Long longUpdateDate = formResponse.getUpdate( ).getTime( );
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_DATE_UPDATE, longUpdateDate );
        
        if ( formResponseState != null )
        {
            // --- id form response workflow state
            int nIdFormResponseWorkflowState = formResponseState.getId( );
            solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, String.valueOf( nIdFormResponseWorkflowState ) );

            // --- form response workflow state title
            String strFormResponseWorkflowStateTitle = manageNullValue( formResponseState.getName( ) );
            solrItem.addDynamicField( FormResponseSearchItem.FIELD_TITLE_WORKFLOW_STATE, strFormResponseWorkflowStateTitle );
        }
        
        return solrItem;
    }
    
    private void indexResponse(SolrItem solrItem, FormQuestionResponse formQuestionResponse, Response response, int formResponseId)
    {
    	Set<String> setFieldNameBuilderUsed = new HashSet<>( );
    	String strQuestionCode = formQuestionResponse.getQuestion( ).getCode( );
    	if ( !StringUtils.isEmpty( response.getResponseValue( ) ) )
        {
            StringBuilder fieldNameBuilder = new StringBuilder(
            		createSolrEntryKey( strQuestionCode, response.getIterationNumber( ), response ) );
            if ( !setFieldNameBuilderUsed.contains( fieldNameBuilder.toString( ) ) )
            {
                setFieldNameBuilderUsed.add( fieldNameBuilder.toString( ) );
                indexResponseValue(solrItem, formQuestionResponse, response, fieldNameBuilder);
            }
            else
            {
                AppLogService.error( " FieldNameBuilder {}  already used for formResponse.getId( )  {}  formQuestionResponse.getId( )  {} response.getIdResponse( ) {}",
                		fieldNameBuilder.toString( ), formResponseId, formQuestionResponse.getId( ), response.getIdResponse( ) );
            }
        }
    }
    
    private void indexResponseValue(SolrItem solrItem, FormQuestionResponse formQuestionResponse, Response response, StringBuilder fieldNameBuilder)
    {
        Entry entry = formQuestionResponse.getQuestion( ).getEntry( );
    	IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( entry );
    	if ( entryTypeService instanceof EntryTypeDate )
        {
            try
            {
                Long timestamp = Long.valueOf( response.getResponseValue( ) );
                solrItem.addDynamicField( fieldNameBuilder.toString( ) + FormResponseSearchItem.FIELD_DATE_SUFFIX, timestamp );
            }
            catch( Exception e )
            {
                AppLogService.error( "Unable to parse {}", response.getResponseValue( ), e );
            }
        }
        else
            if ( entryTypeService instanceof EntryTypeNumbering )
            {
                try
                {
                    solrItem.addDynamicField( fieldNameBuilder.toString( ) + FormResponseSearchItem.FIELD_INT_SUFFIX,
                            response.getResponseValue( ) );
                }
                catch( NumberFormatException e )
                {
                    AppLogService.error( "Unable to parse {} to integer ", response.getResponseValue( ), e );
                }
            }
            else
            	if (entryTypeService instanceof EntryTypeGeolocation)
            	{
            		GeolocItem geolocItem = new GeolocItem(  );
            		//todo
            		HashMap<String, Object> gref = new HashMap<>(  );
            		gref.put( "coordinates", Arrays.asList( new Double[] { 2.31272, 48.83632 } ) );
            		geolocItem.setGeometry( gref );
            		solrItem.addDynamicFieldGeoloc("", geolocItem, "");
            	}
            	else
	            {
            		solrItem.addDynamicField( fieldNameBuilder.toString( ), response.getResponseValue( ) );
	            }
    }

    /**
     * Concatenates the value of the specified field in this record
     * @param formQuestionResponseList 
     * @param form 
     * 
     * @param record
     *            the record to seek
     * @param listEntry
     *            the list of field to concatenate
     * @param plugin
     *            the plugin object
     * @return
     */
    private String getContentToIndex( List<FormQuestionResponse> formQuestionResponseList, Form form )
    {

        StringBuilder sb = new StringBuilder( );
        List<Question> listQuestions = QuestionHome.getListQuestionByIdForm(form.getId());

        for ( FormQuestionResponse formQuestionResponse : formQuestionResponseList )
        {
        	Question question = listQuestions.stream( ).filter( qa -> qa.getId( ) == formQuestionResponse.getQuestion( ).getId( ) ).findFirst( ).get( );

            // Only index the indexable entries
            if ( question != null )
            {
                Entry entry = question.getEntry( );
                for ( Response response : formQuestionResponse.getEntryResponse( ) )
                {

                    String responseString = EntryTypeServiceManager.getEntryTypeService( entry ).getResponseValueForExport( entry, null, response, null );
                    if ( !StringUtils.isEmpty( responseString ) )
                    {
                        sb.append( responseString );
                        sb.append( " " );
                    }
                }
            }
        }

        return sb.toString( );
    }

    /**
     * Get the field name
     * 
     * @param responseField
     * @param response
     * @return the field name
     */
    private String getFieldName( fr.paris.lutece.plugins.genericattributes.business.Field responseField, Response response )
    {
        if ( responseField.getIdField( ) > 0 )
        {
            return String.valueOf( responseField.getIdField( ) );
        }
        if ( !StringUtils.isEmpty( responseField.getCode( ) ) )
        {
            return responseField.getCode( );
        }
        if ( !StringUtils.isEmpty( responseField.getTitle( ) ) )
        {
            return responseField.getTitle( );
        }
        return String.valueOf( response.getIdResponse( ) );
    }

    /**
     * Manage a given string null value
     * 
     * @param strValue
     * @return the string if not null, empty string otherwise
     */
    private String manageNullValue( String strValue )
    {
        if ( strValue == null )
        {
            return StringUtils.EMPTY;
        }
        return strValue;
    }
    
    /**
     * Creates the lucene index key.
     * 
     * @param strQuestionCode
     * @param nIterationNumber
     * @return key
     */
    private String createSolrEntryKey( String strQuestionCode, int nIterationNumber, Response response )
    {
        StringBuilder fieldNameBuilder = new StringBuilder( FormResponseSearchItem.FIELD_ENTRY_CODE_SUFFIX );
        fieldNameBuilder.append( strQuestionCode );
        fieldNameBuilder.append( FormResponseSearchItem.FIELD_RESPONSE_FIELD_ITER );

        fr.paris.lutece.plugins.genericattributes.business.Field responseField = response.getField( );

        if ( nIterationNumber == -1 )
        {
            nIterationNumber = 0;
        }
        fieldNameBuilder.append( nIterationNumber );
        
        if ( responseField != null )
        {
            String getFieldName = getFieldName( responseField, response );
            fieldNameBuilder.append( FormResponseSearchItem.FIELD_RESPONSE_FIELD_SEPARATOR );
            fieldNameBuilder.append( getFieldName );
        }
        
        return fieldNameBuilder.toString( );
    }
}
