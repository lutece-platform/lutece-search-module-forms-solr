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

public class Utilities
{

    public static final String FORMS_NAME = "forms-solr.indexer.name";
    public static final String FORMS_DESCRIPTION = "forms-solr.indexer.description";
    public static final String PROPERTY_INDEXER_ENABLE = "forms-solr.indexer.enable";
    public static final String PROPERTY_INDEXER_VERSION = "forms-solr.indexer.version";
    public static final String PROPERTY_BATCH = "forms-solr.index.writer.commit.size";
    public static final String RESOURCE_TYPE_FORMS = "forms";
    public static final String SHORT_NAME_FORMS = "formsResponse";
    public static final String SHORT_ROLE_FORMS = "formResponse";
    public static final String DOC_INDEXATION_ERROR = "[SolrFormsResponseIndexer] An error occured during the indexation of the formResponse id: {}";
    public static final String DOC_DELETE_BY_QUERY_ERROR = "[SolrFormsResponseIndexer] An error occured during the delete by query of the formResponse id: {}";

    /**
     * Private constructor - this class does not need to be instantiated
     */
    private Utilities( )
    {
    }
}
