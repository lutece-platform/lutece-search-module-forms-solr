/*
 * Copyright (c) 2002-2022, City of Paris
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.util.ClientUtils;
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
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeCheckBox;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeDate;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeGeolocation;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeNumbering;
import fr.paris.lutece.plugins.forms.util.LuceneUtils;
import fr.paris.lutece.plugins.genericattributes.business.FieldHome;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.search.solr.business.SolrServerService;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.search.solr.util.LuteceSolrRuntimeException;
import fr.paris.lutece.plugins.search.solr.util.SolrConstants;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.business.state.StateFilter;
import fr.paris.lutece.plugins.workflowcore.service.resource.IResourceWorkflowService;
import fr.paris.lutece.plugins.workflowcore.service.state.IStateService;
import fr.paris.lutece.portal.service.search.SearchItem;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

/**
 * The Solr Response Forms indexer service for Solr.
 *
 */
public class SolrFormsIndexer implements SolrIndexer
{
    private static final int TAILLE_LOT = AppPropertiesService.getPropertyInt( Utilities.PROPERTY_BATCH, 100 );
    private static final List<String> LIST_RESSOURCES_NAME = new ArrayList<>( );

    @Autowired( required = false )
    private IResourceWorkflowService _resourceWorkflowService;

    @Autowired( required = false )
    private IStateService _stateService;


    /**
     * Create Solr Response Form Indexer
     */
    private SolrFormsIndexer( )
    {
    }
    
    public static void initListResourceName( List<String> listResourceName ) 
    {
    	LIST_RESSOURCES_NAME.addAll( listResourceName );    	 
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public String getName( )
    {
        return AppPropertiesService.getProperty( Utilities.FORMS_NAME );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription( )
    {
        return AppPropertiesService.getProperty( Utilities.FORMS_DESCRIPTION );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnable( )
    {
        return "true".equalsIgnoreCase( AppPropertiesService.getProperty( Utilities.PROPERTY_INDEXER_ENABLE ) );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getResourceUid( String strResourceId, String strResourceType )
    {
        StringBuilder stringBuilder = new StringBuilder( strResourceId );
        stringBuilder.append( "_" ).append( FormResponse.RESOURCE_TYPE );

        return stringBuilder.toString( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getResourcesName( )
    {
        return LIST_RESSOURCES_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getVersion( )
    {
        return AppPropertiesService.getProperty( Utilities.PROPERTY_INDEXER_ENABLE );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Field> getAdditionalFields( )
    {
        // To be implemented later to add facets
        return new ArrayList<>( );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<SolrItem> getDocuments( String idFormResponse )
    {
        final FormResponse formResponse = FormResponseHome.findByPrimaryKeyForIndex( Integer.parseInt( idFormResponse ) );
       
        if( !formResponse.isPublished( ) ) {
        	// delete all index linked to uid. We get the uid of the resource to prefix it like we do during the indexation         
        	try {
				SolrServerService.getInstance( ).getSolrServer( ).deleteByQuery( SearchItem.FIELD_UID + ":" + ClientUtils.escapeQueryChars(SolrIndexerService.getWebAppName( )) + SolrConstants.CONSTANT_UNDERSCORE
				        + getResourceUid( String.valueOf( formResponse.getId( )), FormResponse.RESOURCE_TYPE ) );
			
        	} catch (SolrServerException | IOException e)  {
				
				AppLogService.error( Utilities.DOC_DELETE_BY_QUERY_ERROR, idFormResponse, e );
	            throw new LuteceSolrRuntimeException( e.getMessage( ), e );
			} 
        	return Collections.emptyList( );  
        } 
        
        Form form = FormHome.findByPrimaryKey( formResponse.getFormId( ) );
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
        SolrItem solrItem = null;
    	List<FormQuestionResponse> listFormsQuestionResponse= formResponse.getSteps( ).stream( ).flatMap( step -> step.getQuestions( ).stream( ) ).collect( Collectors.toList( ));
        try
        {
        	solrItem = getSolrItem( formResponse, form, formResponseState,
            		listFormsQuestionResponse ,
        		    listFormsQuestionResponse.stream( ).filter(fqr -> fqr.getQuestion().getEntry()!= null && fqr.getQuestion().getEntry().getFields( ) != null ).flatMap(fqr -> fqr.getQuestion().getEntry().getFields( ).stream( ))
        		    .collect(Collectors.groupingBy( fr.paris.lutece.plugins.genericattributes.business.Field::getIdField ))
            );
        }
        catch( Exception e )
        {
            AppLogService.error( Utilities.DOC_INDEXATION_ERROR, idFormResponse, e );
            throw new LuteceSolrRuntimeException( e.getMessage( ), e );
        }

        return Arrays.asList( solrItem );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> indexDocuments( )
    {
        List<String> errors = new ArrayList<>( );
        final List<Integer> listFormResponsesId = FormResponseHome.selectAllFormResponsesId( );
        final List<State> listState = ( _stateService != null && _resourceWorkflowService != null ) ? _stateService.getListStateByFilter( new StateFilter( ) )
                : new ArrayList<>( );
        State defaultFormResponseState = new State( );
        defaultFormResponseState.setId( -1 );
        defaultFormResponseState.setName( StringUtils.EMPTY );

        IntStream.range( 0, ( listFormResponsesId.size( ) + TAILLE_LOT - 1 ) / TAILLE_LOT )
                .mapToObj( i -> listFormResponsesId.subList( i * TAILLE_LOT, Math.min( listFormResponsesId.size( ), ( i + 1 ) * TAILLE_LOT ) ) )
                .forEach( batch -> {
                    try
                    {

                        indexingByBatch( batch, listState, FormHome.getFormList( ), defaultFormResponseState );

                    }
                    catch( IOException e )
                    {

                        AppLogService.error( e.getMessage( ), e );
                        errors.add( SolrIndexerService.buildErrorMessage( e ) );
                        return;
                    }
                } );

        return errors;
    }

    /**
     * Indexing Form Responses by batch
     * 
     * @param FormResponsesIdBatch
     *            the list of Form Responses Id
     * @param listState
     *            the list of all workflow State
     * @param lisForms
     *            the form list
     * @param defaultFormResponseState
     *            the default Form ResponseState
     * @throws IOException
     *             the IOException
     */
    private void indexingByBatch( List<Integer> formResponsesIdBatch, final List<State> listState, final List<Form> listForms,
            final State defaultFormResponseState ) throws IOException
    {
        Map<Integer, Integer> mapIdState = new HashMap<>( );
        // we filter the formResponse on the status to index only the published formsResponse
    	List<FormResponse> listFormReesponse= FormResponseHome.getFormResponseUncompleteByPrimaryKeyList( formResponsesIdBatch )
  				.stream().filter(FormResponse::isPublished).collect(Collectors.toList( ));    	
        List<Integer>  formResponseIdList= listFormReesponse.stream().map( FormResponse::getId ).collect(Collectors.toList( ));

        if ( _resourceWorkflowService != null )
        {

            listForms.forEach( form -> mapIdState.putAll( _resourceWorkflowService.getListIdStateByListId( formResponseIdList, form.getIdWorkflow( ),
                    FormResponse.RESOURCE_TYPE, form.getId( ) ) ) );
        }
        
        List<FormQuestionResponse> listFormQuestionResponse =FormQuestionResponseHome.getFormQuestionResponseListByFormResponseList( formResponseIdList );
        Set<Integer> listIdEntry =new HashSet<>();
        listFormQuestionResponse.forEach( fqr -> fqr.getEntryResponse( ).forEach( rsp -> listIdEntry.add( rsp.getEntry().getIdEntry( ))));
        SolrIndexerService
                .write( getSolrItems( FormResponseHome.getFormResponseUncompleteByPrimaryKeyList( formResponseIdList ),
                		formResponseIdList.stream( )
                                .collect( Collectors.toMap( formResponseId -> formResponseId, formResponseId -> listState.stream( )
                                        .filter( state -> ( mapIdState.get( formResponseId ) != null && state.getId( ) == mapIdState.get( formResponseId ) ) )
                                        .findAny( ).orElse( defaultFormResponseState ) ) ),
                        listForms.stream( ).collect( Collectors.toMap( Form::getId, Function.identity( ) ) ),
                        listFormQuestionResponse.stream( )
                                .collect( Collectors.groupingBy( FormQuestionResponse::getIdFormResponse ) ),
                        QuestionHome.findByPrimaryKeyList( listFormQuestionResponse.stream( )
                           		.map( reponse ->  reponse.getQuestion().getId( ) )
                           		.distinct( ).collect(Collectors.toList( ))).stream().collect(Collectors.toMap( Question::getId, Function.identity( ) )),
                        FieldHome.getFieldListByListIdEntry(new ArrayList<>( listIdEntry ))
                		.stream().collect(Collectors.groupingBy( fr.paris.lutece.plugins.genericattributes.business.Field::getIdField  ))
                		
                		) );
    }

    /**
     * Builds a documents which will be used by solr during the indexing of the form responses
     *
     * @param listFormResponse
     *            the form responses list
     * @param mapResourceState
     *            the resource state list grouping by FormResponseId: Map<FormResponseId, State>
     * @param mapFom
     *            the form list grouping by form Id: Map<FormId, Form>
     * @param mapFormQuestionResponse
     *            the Form Question Responses list grouping byFormResponseId: Map<FormResponseId, List<FormQuestionResponse>>
     * @param mapQuestions
     * 				the question form map
     * @param mapFileds
     *            the Filed list grouping by Field Id: Map<FieldId, Field>
   
     * @return collection of SolrItem
     */
   
    private Collection<SolrItem> getSolrItems( List<FormResponse> listFormResponse, Map<Integer, State> mapResourceState, Map<Integer, Form> mapFom,
            Map<Integer, List<FormQuestionResponse>> mapFormQuestionResponse, Map< Integer,Question > mapQuestions, 
            Map<Integer, List<fr.paris.lutece.plugins.genericattributes.business.Field>> mapFileds)
    {
        Collection<SolrItem> solrItemList = new ArrayList<>( );
        for ( FormResponse formResponse : listFormResponse )
        {
        	List<FormQuestionResponse> formQuestionResponseList=  mapFormQuestionResponse.get( formResponse.getId( ) );
        	formQuestionResponseList.forEach(fqr ->fqr.setQuestion(mapQuestions.get(fqr.getQuestion().getId( ))));            
        	solrItemList.add( getSolrItem( formResponse, mapFom.get( formResponse.getFormId( ) ), mapResourceState.get( formResponse.getId( ) ), formQuestionResponseList, mapFileds ));
        }
        return solrItemList;
    }

    /**
     * Builds a document which will be used by solr during the indexing of the form responses
     * 
     * @param formResponse
     *            the form reponse
     * @param form
     *            the form
     * @param formResponseState
     *            the form Response State
     * @param formQuestionResponseList
     *            the form Question Response List
     * @param mapFileds
     *            the Filed list grouping by Field Id: Map<FieldId, Field>
     * @return the SolrItem builded
     */
    private SolrItem getSolrItem( FormResponse formResponse, Form form, State formResponseState, List<FormQuestionResponse> formQuestionResponseList, 
    		Map<Integer, List<fr.paris.lutece.plugins.genericattributes.business.Field>> mapFileds )
    {
        SolrItem solrItem = initSolrItem( formResponse, form, formResponseState, formQuestionResponseList );
        // --- form response entry code / fields
        Set<String> setFieldNameBuilderUsed = new HashSet<>( );
        IEntryTypeService typerService = null;
       for ( FormQuestionResponse formQuestionResponse : formQuestionResponseList )
        {
            for ( Response response : formQuestionResponse.getEntryResponse( ) )
            {
                typerService = EntryTypeServiceManager.getEntryTypeService( response.getEntry( ) );
                if( typerService instanceof EntryTypeGeolocation ) {
                	
                	addDynamicFieldGeoloc( formQuestionResponse.getEntryResponse( ), mapFileds ,solrItem,formQuestionResponse.getQuestion( ).getCode( ), setFieldNameBuilderUsed);
                	break;
                }
                // add the Response Value to solrItem
                addResponseValue( solrItem, formQuestionResponse.getQuestion( ).getCode( ), typerService, response, formResponse.getId( ),
                        setFieldNameBuilderUsed );
            }
        }
        return solrItem;
    }

    /**
     * initiate the build of an document which will be used by solr during the indexing of the form responses
     * 
     * @param formResponse
     *            the form reponse
     * @param form
     *            the form
     * @param formResponseState
     *            the form Response State
     * @param formQuestionResponseList
     *            the form Question Response List
     * @return the SolrItem builded
     */
    private SolrItem initSolrItem( FormResponse formResponse, Form form, State formResponseState, List<FormQuestionResponse> formQuestionResponseList )
    {
        // make a new, empty SolrItem
        SolrItem solrItem = new SolrItem( );
        String nIdFormResponse = String.valueOf(formResponse.getId( ));
        solrItem.setIdResource(  nIdFormResponse );
        solrItem.setSite( SolrIndexerService.getWebAppName( ) );
        solrItem.setRole( Utilities.SHORT_ROLE_FORMS );
        solrItem.setType( FormResponse.RESOURCE_TYPE + "_" +form.getId( ) );
        solrItem.setUid( getResourceUid(  nIdFormResponse, FormResponse.RESOURCE_TYPE) );
        solrItem.setTitle( Utilities.SHORT_ROLE_FORMS + " #" + nIdFormResponse );
        solrItem.setDate( formResponse.getCreation( ) );
        solrItem.setUrl( "jsp/site/Portal.jsp?page=formsResponse&id_response="+nIdFormResponse );

        // --- form response identifier
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, Long.valueOf( nIdFormResponse ) );

        // --- field contents
        solrItem.setContent( getContentToIndex( formQuestionResponseList ) );

        // --- form title
        solrItem.addDynamicFieldNotAnalysed( FormResponseSearchItem.FIELD_FORM_TITLE, form.getTitle( ) );

        // --- id form
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_FORM, Long.valueOf( form.getId( ) ) );

        // --- form response date create
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_DATE_CREATION, formResponse.getCreation( ).getTime( ) );

        // --- form response date closure
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_DATE_UPDATE, formResponse.getUpdate( ).getTime( ) );

        // --- id form response workflow state
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_WORKFLOW_STATE, Long.valueOf( formResponseState.getId( ) ) );

        // --- form response workflow state title
        solrItem.addDynamicFieldNotAnalysed( FormResponseSearchItem.FIELD_TITLE_WORKFLOW_STATE, formResponseState.getName( ) );

        return solrItem;
    }

    /**
     * Add Response Value from question reponse to solrItem object
     * 
     * @param solrItem
     *            the solrItem object
     * @param codeQuestion
     *            the Question code
     * @param entryTypeService
     *            the EntryTypeService
     * @param response
     *            the Response
     * @param formResponseId
     *            the form Response Id
     * @param setFieldNameBuilderUsed
     */
    private void addResponseValue( SolrItem solrItem, String codeQuestion, IEntryTypeService entryTypeService, Response response, int formResponseId,
            Set<String> setFieldNameBuilderUsed )
    {

        if ( StringUtils.isNotEmpty( response.getResponseValue( ) ) )
        {
            StringBuilder fieldNameBuilder = new StringBuilder( LuceneUtils.createLuceneEntryKey( codeQuestion, response.getIterationNumber( ) ) );
            if ( !setFieldNameBuilderUsed.contains( fieldNameBuilder.toString( ) ) || ( entryTypeService instanceof EntryTypeCheckBox ) )
            {
                setFieldNameBuilderUsed.add( fieldNameBuilder.toString( ) );
                addResponseValueByType( solrItem, entryTypeService, response, fieldNameBuilder );
            }
            else
            {
                AppLogService.error( " FieldNameBuilder {}  already used for formResponse.getId( )  {}  codeQuestion  {} response.getIdResponse( ) {}",
                        fieldNameBuilder.toString( ), formResponseId, codeQuestion, response.getIdResponse( ) );
            }
        }
    }

    /**
     * Add Response Value By Type of the Entry to solrItem object
     * 
     * @param solrItem
     *            the SolrItem object
     * @param entryTypeService
     *            the EntryTypeService
     * @param response
     *            the response
     * @param fieldNameBuilder
     *            the field Name
     */
    private void addResponseValueByType( SolrItem solrItem, IEntryTypeService entryTypeService, Response response, StringBuilder fieldNameBuilder )
    {

        if ( entryTypeService instanceof EntryTypeDate )
        {

            solrItem.addDynamicField( fieldNameBuilder.toString( ) + FormResponseSearchItem.FIELD_DATE_SUFFIX, Long.valueOf( response.getResponseValue( ) ) );

        }
        else
            if ( entryTypeService instanceof EntryTypeNumbering )
            {

                solrItem.addDynamicField( fieldNameBuilder.append( FormResponseSearchItem.FIELD_INT_SUFFIX ).toString( ),
                        Long.parseLong( response.getResponseValue( ) ) );

            }
            else
                if ( entryTypeService instanceof EntryTypeGeolocation )
                {
                    // The built of the address is to be tested...!!!
                    solrItem.addDynamicFieldGeoloc( fieldNameBuilder.toString( ), response.getResponseValue( ), fieldNameBuilder.toString( ) );

                }else if ( entryTypeService instanceof EntryTypeCheckBox  ) {
                	
                	List<String> dfListBox= (List<String>) solrItem.getDynamicFields().get( fieldNameBuilder.toString( ) + SolrItem.DYNAMIC_LIST_FIELD_SUFFIX );
                	
                	if( dfListBox != null ) 
                	{
                		dfListBox.add( response.getResponseValue( ) );
                	}else
                	{
                		
                		dfListBox= new ArrayList< >( Arrays.asList( response.getResponseValue( )));
                	}
                	solrItem.addDynamicField( fieldNameBuilder.toString( ), dfListBox);
                }
                else
                {
                    solrItem.addDynamicField( fieldNameBuilder.toString( ), response.getResponseValue( ) );
                }
    }

    /**
     * Concatenates the value of the specified field in this record
     * 
     * @param record
     *            the record to seek
     * @param listEntry
     *            the list of field to concatenate
     * @param plugin
     *            the plugin object
     * @return the builded content
     */
    private String getContentToIndex( List<FormQuestionResponse> listFormQuestionResponse )
    {

        StringBuilder sb = new StringBuilder( );
        for ( FormQuestionResponse questionResponse : listFormQuestionResponse )
        {
            for ( Response response : questionResponse.getEntryResponse( ) )
            {
                String responseString = EntryTypeServiceManager.getEntryTypeService( response.getEntry( ) ).getResponseValueForExport( response.getEntry( ),
                        null, response, null );
                if ( !StringUtils.isEmpty( responseString ) )
                {
                    sb.append( responseString );
                    sb.append( " " );
                }
            }
        }

        return sb.toString( );
    }
    /**
     * 
     * @param listResponse
     * 			  the list of Response
     * @param mapFileds
     *            the Filed list grouping by Field Id: Map<FieldId, Field>
     * @param solrItem
     * 			the solr item
     * @param codeQuestion
     * 			the question code
     * @param setFieldNameBuilderUsed
     * 			the 
     */
    private void addDynamicFieldGeoloc( List<Response> listResponse, 
    		Map<Integer,List<fr.paris.lutece.plugins.genericattributes.business.Field>> mapFileds, 
    		SolrItem solrItem, String codeQuestion, Set<String> setFieldNameBuilderUsed )
    {
    	double x;
    	double y;
    	String address;    	  
        for (Map.Entry<Integer, List<Response>> mapentry : listResponse.stream().collect(Collectors.groupingBy( Response::getIterationNumber )).entrySet()) {
       	 		x=0; y=0; address=null;
        		String fieldNameBuilder =  LuceneUtils.createLuceneEntryKey( codeQuestion,  mapentry.getKey( ) ) ;
        		if ( !setFieldNameBuilderUsed.contains( fieldNameBuilder ) )
                {
                     setFieldNameBuilderUsed.add( fieldNameBuilder );
                    for ( Response response : mapentry.getValue() )
     		        {     		        
     		            switch( mapFileds.get(response.getField().getIdField( )).get(0).getValue( ) )
     		            {
     		                case IEntryTypeService.FIELD_ADDRESS:
     		                	address= response.getResponseValue( );
     		                    break;
     		
     		                case IEntryTypeService.FIELD_X:
     		                    x= StringUtils.isEmpty(response.getResponseValue( ))? 0: Double.parseDouble( response.getResponseValue( ));
     		                    break;
     		
     		                case IEntryTypeService.FIELD_Y:
     		                	y= StringUtils.isEmpty(response.getResponseValue( ))? 0: Double.parseDouble( response.getResponseValue( ));
     		                    break;
     		                default:
     		         
     		                	break;
     		            }
     		        }
     		        if( address != null && x != 0 && y != 0 ) {
     		        	
     		        	solrItem.addDynamicFieldGeoloc( fieldNameBuilder , address, x, y, FormResponse.RESOURCE_TYPE);
     		        }
                 
                 }
                 else
                 {
                     AppLogService.error( " FieldNameBuilder {}  already used for  {}  codeQuestion  {} ",
                             fieldNameBuilder, codeQuestion );
                 }
        }
         
       }

}
