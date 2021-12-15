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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import fr.paris.lutece.plugins.forms.business.Form;
import fr.paris.lutece.plugins.forms.business.FormHome;
import fr.paris.lutece.plugins.forms.business.FormQuestionResponse;
import fr.paris.lutece.plugins.forms.business.FormResponse;
import fr.paris.lutece.plugins.forms.business.FormResponseHome;
import fr.paris.lutece.plugins.forms.business.FormResponseStep;
import fr.paris.lutece.plugins.forms.business.form.search.FormResponseSearchItem;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeDate;
import fr.paris.lutece.plugins.forms.service.entrytype.EntryTypeNumbering;
import fr.paris.lutece.plugins.forms.util.LuceneUtils;
import fr.paris.lutece.plugins.genericattributes.business.Entry;
import fr.paris.lutece.plugins.genericattributes.business.Response;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.EntryTypeServiceManager;
import fr.paris.lutece.plugins.genericattributes.service.entrytype.IEntryTypeService;
import fr.paris.lutece.plugins.search.solr.business.field.Field;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexer;
import fr.paris.lutece.plugins.search.solr.indexer.SolrIndexerService;
import fr.paris.lutece.plugins.search.solr.indexer.SolrItem;
import fr.paris.lutece.plugins.workflowcore.business.state.State;
import fr.paris.lutece.plugins.workflowcore.service.state.StateService;
import fr.paris.lutece.portal.service.message.SiteMessageException;
import fr.paris.lutece.portal.service.search.IndexationService;
import fr.paris.lutece.portal.service.search.SearchIndexer;
import fr.paris.lutece.portal.service.search.SearchItem;
import fr.paris.lutece.portal.service.util.AppLogService;
import fr.paris.lutece.portal.service.util.AppPropertiesService;

public class SolrFormsIndexer implements SolrIndexer
{
    private static final String FILTER_DATE_FORMAT = AppPropertiesService.getProperty( "forms-solr.index.date.format", "dd/MM/yyyy" );
    private static final int TAILLE_LOT = AppPropertiesService.getPropertyInt( "forms-solr.index.writer.commit.size", 100 );

    private static AtomicBoolean _bIndexToLunch = new AtomicBoolean( false );
    private static AtomicBoolean _bIndexIsRunning = new AtomicBoolean( false );

    @Autowired( required = false )
    private StateService _stateService;

    @Override
    public List<Field> getAdditionalFields( )
    {
        return new ArrayList<>( );
    }

    @Override
    public String getDescription( )
    {
        return Utilities.FORMS_DESCRIPTION;
    }

    @Override
    public List<SolrItem> getDocuments( String arg0 )
    {
        return new ArrayList<>( );
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
            AppLogService.error( "SolrAppointmentIndexer, unknown resourceType: " + strResourceType );
            return null;
        }
        return stringBuilder.toString( );
    }

    @Override
    public List<String> getResourcesName( )
    {
        return new ArrayList<>( );
    }

    @Override
    public String getVersion( )
    {
        return Utilities.FORMS_VERSION;
    }

    @Override
    public List<String> indexDocuments( )
    {
        List<String> errors = new ArrayList<>( );
        try
        {
            indexFormsResponse( );
        }
        catch( IOException | SiteMessageException e )
        {
            AppLogService.error( "Error indexing FormResponses", e );
            errors.add( e.toString( ) );
        }
        catch( InterruptedException e )
        {
            AppLogService.error( "Error indexing FormResponses", e );
            errors.add( e.toString( ) );
            Thread.currentThread( ).interrupt( );
        }
        return errors;
    }

    @Override
    public boolean isEnable( )
    {
        return Boolean.valueOf( AppPropertiesService.getProperty( Utilities.PROPERTY_INDEXER_ENABLE ) );
    }

    public synchronized void indexFormsResponse( ) throws IOException, InterruptedException, SiteMessageException
    {
        List<Integer> listFormResponsesId = FormResponseHome.selectAllFormResponsesId( );

        _bIndexToLunch.set( true );
        if ( _bIndexIsRunning.compareAndSet( false, true ) )
        {
            new Thread( ( ) -> {
                try
                {
                    List<FormResponse> listFormResponses = new ArrayList<>( TAILLE_LOT );
                    for ( Integer nIdFormResponse : listFormResponsesId )
                    {
                        FormResponse response = FormResponseHome.findByPrimaryKeyForIndex( nIdFormResponse );
                        if ( response != null )
                        {
                            listFormResponses.add( response );
                        }
                        if ( listFormResponses.size( ) == TAILLE_LOT )
                        {
                            indexFormResponseList( listFormResponses );
                            listFormResponses.clear( );
                        }
                    }
                    indexFormResponseList( listFormResponses );
                }
                catch( Exception e )
                {
                    AppLogService.error( e.getMessage( ), e );
                    Thread.currentThread( ).interrupt( );
                }
                finally
                {
                    _bIndexIsRunning.set( false );
                }

            } ).start( );
        }
    }

    /**
     * {@inheritDoc}
     */
    private void indexFormResponseList( List<FormResponse> listFormResponse )
    {
        Map<Integer, Form> mapForms = FormHome.getFormList( ).stream( ).collect( Collectors.toMap( Form::getId, form -> form ) );
        List<SolrItem> solrItemList = new ArrayList<>( );
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
                solrItem = getSolrItem( formResponse, form, formResponseState );
            }
            catch( Exception e )
            {
                IndexationService.error( (SearchIndexer) this, e, null );
            }

            if ( solrItem != null )
            {
                solrItemList.add( solrItem );
            }
        }
        addSolrItems( solrItemList );
    }

    private void addSolrItems( List<SolrItem> solrItemList )
    {
        try
        {
            for ( SolrItem solrItem : solrItemList )
            {
                SolrIndexerService.write( solrItem );
            }
        }
        catch( IOException e )
        {
            AppLogService.error( "Unable to index form response", e );
        }
        solrItemList.clear( );
    }

    private SolrItem getSolrItem( FormResponse formResponse, Form form, State formResponseState )
    {
        // make a new, empty SolrItem
        SolrItem solrItem = new SolrItem( );

        int nIdFormResponse = formResponse.getId( );

        solrItem.setContent( StringUtils.EMPTY );
        solrItem.setSite( SolrIndexerService.getWebAppName( ) );
        solrItem.setRole( Utilities.SHORT_ROLE_FORMS );
        solrItem.setType( Utilities.SHORT_NAME_FORMS );
        solrItem.setUid( String.valueOf( nIdFormResponse ) );
        solrItem.setTitle( Utilities.SHORT_ROLE_FORMS + " #" + nIdFormResponse );
        solrItem.setDate( formResponse.getCreation( ) );

        // --- form response identifier
        solrItem.addDynamicField( FormResponseSearchItem.FIELD_ID_FORM_RESPONSE, String.valueOf( nIdFormResponse ) );

        // --- field contents
        solrItem.addDynamicField( SearchItem.FIELD_CONTENTS, manageNullValue( getContentToIndex( formResponse ) ) );

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

        // --- form response entry code / fields
        Set<String> setFieldNameBuilderUsed = new HashSet<>( );
        for ( FormResponseStep formResponseStep : formResponse.getSteps( ) )
        {
            for ( FormQuestionResponse formQuestionResponse : formResponseStep.getQuestions( ) )
            {
                String strQuestionCode = formQuestionResponse.getQuestion( ).getCode( );
                Entry entry = formQuestionResponse.getQuestion( ).getEntry( );
                IEntryTypeService entryTypeService = EntryTypeServiceManager.getEntryTypeService( entry );

                for ( Response response : formQuestionResponse.getEntryResponse( ) )
                {
                    fr.paris.lutece.plugins.genericattributes.business.Field responseField = response.getField( );

                    if ( !StringUtils.isEmpty( response.getResponseValue( ) ) )
                    {
                        StringBuilder fieldNameBuilder = new StringBuilder(
                                LuceneUtils.createLuceneEntryKey( strQuestionCode, response.getIterationNumber( ) ) );

                        if ( responseField != null )
                        {
                            String getFieldName = getFieldName( responseField, response );
                            fieldNameBuilder.append( FormResponseSearchItem.FIELD_RESPONSE_FIELD_SEPARATOR );
                            fieldNameBuilder.append( getFieldName );
                        }

                        if ( !setFieldNameBuilderUsed.contains( fieldNameBuilder.toString( ) ) )
                        {
                            setFieldNameBuilderUsed.add( fieldNameBuilder.toString( ) );
                            if ( entryTypeService instanceof EntryTypeDate )
                            {
                                try
                                {
                                    Long timestamp = Long.valueOf( response.getResponseValue( ) );
                                    solrItem.addDynamicField( fieldNameBuilder.toString( ) + FormResponseSearchItem.FIELD_DATE_SUFFIX, timestamp );
                                }
                                catch( Exception e )
                                {
                                    AppLogService.error( "Unable to parse " + response.getResponseValue( ) + " with date formatter " + FILTER_DATE_FORMAT, e );
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
                                        AppLogService.error( "Unable to parse " + response.getResponseValue( ) + " to integer ", e );
                                    }
                                }
                                else
                                {
                                    solrItem.addDynamicField( fieldNameBuilder.toString( ), response.getResponseValue( ) );
                                }

                        }
                        else
                        {
                            AppLogService.error( " FieldNameBuilder " + fieldNameBuilder.toString( ) + "  already used for formResponse.getId( )  "
                                    + formResponse.getId( ) + "  formQuestionResponse.getId( )  " + formQuestionResponse.getId( )
                                    + " response.getIdResponse( ) " + response.getIdResponse( ) + " formResponseStep" + formResponseStep.getId( ) );

                        }

                    }
                }
            }
        }

        return solrItem;
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
     * @return
     */
    private String getContentToIndex( FormResponse formResponse )
    {

        StringBuilder sb = new StringBuilder( );

        for ( FormResponseStep formResponseStep : formResponse.getSteps( ) )
        {
            for ( FormQuestionResponse questionResponse : formResponseStep.getQuestions( ) )
            {

                // Only index the indexable entries
                if ( questionResponse.getQuestion( ).isResponsesIndexed( ) )
                {
                    Entry entry = questionResponse.getQuestion( ).getEntry( );
                    for ( Response response : questionResponse.getEntryResponse( ) )
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
}
