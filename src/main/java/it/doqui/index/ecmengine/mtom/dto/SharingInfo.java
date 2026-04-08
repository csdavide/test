/* Index ECM Engine - A system for managing the capture (when created
 * or received), classification (cataloguing), storage, retrieval,
 * revision, sharing, reuse and disposition of documents.
 *
 * Copyright (C) 2008 Regione Piemonte
 * Copyright (C) 2008 Provincia di Torino
 * Copyright (C) 2008 Comune di Torino
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2,
 * or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */

package it.doqui.index.ecmengine.mtom.dto;

/**
 * DTO che rappresenta un contenuto binario su cui si vogliono eseguire delle
 * operazioni di gestione documentale.
 * 
 * @author DoQui
 */
public class SharingInfo extends MtomEngineDto {
    private static final long serialVersionUID = -485541007397421929L;
    
    private String sharedLink;
    private String contentPropertyPrefixedName;
    private String source;
    private String fromDate;
    private String toDate;

    // ########### result properties
    // Content-Disposition
    private String resultContentDisposition;
    // qname metadato contenente il nome del file
    private String resultPropertyPrefixedName;

    public String getSharedLink() {
	return sharedLink;
    }

    public void setSharedLink(String sharedLink) {
	this.sharedLink = sharedLink;
    }

    public String getContentPropertyPrefixedName() {
	return contentPropertyPrefixedName;
    }

    public void setContentPropertyPrefixedName(String contentPropertyPrefixedName) {
	this.contentPropertyPrefixedName = contentPropertyPrefixedName;
    }

    public String getSource() {
	return source;
    }

    public void setSource(String source) {
	this.source = source;
    }

    public String getFromDate() {
	return fromDate;
    }

    public void setFromDate(String fromDate) {
	this.fromDate = fromDate;
    }

    public String getToDate() {
	return toDate;
    }

    public void setToDate(String toDate) {
	this.toDate = toDate;
    }

    public String getResultPropertyPrefixedName() {
	return resultPropertyPrefixedName;
    }

    public void setResultPropertyPrefixedName(String resultPropertyPrefixedName) {
	this.resultPropertyPrefixedName = resultPropertyPrefixedName;
    }

    public String getResultContentDisposition() {
	return resultContentDisposition;
    }

    public void setResultContentDisposition(String resultContentDisposition) {
	this.resultContentDisposition = resultContentDisposition;
    }
}