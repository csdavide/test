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
 * DTO che rappresenta i parametri per la ricerca di associazioni.
 * 
 * @author DoQui
 */
public class AssociationsSearchParams extends MtomEngineDto {
    private static final long serialVersionUID = 8448670975509442536L;
    private int limit;
    private String associationType;
    private int pageSize;
    private int pageIndex;
    private String[] filterType;

    /**
     * Restituisce il numero massimo di associazioni da ricercare.
     */
    public int getLimit() {
	return limit;
    }

    /**
     * Imposta il numero massimo di associazioni da ricercare.
     */
    public void setLimit(int limit) {
	this.limit = limit;
    }

    /**
     * <p>
     * Restituisce il tipo di associazioni da ricercare.
     * </p>
     * <p>
     * Il tipo pu&ograve; essere:
     * </p>
     * <ul>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_PARENT}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_CHILD}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_SOURCE}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_TARGET}</li>
     * </ul>
     */
    public String getAssociationType() {
	return associationType;
    }

    /**
     * <p>
     * Imposta il tipo di associazioni da ricercare.
     * </p>
     * <p>
     * Il tipo pu&ograve; essere:
     * </p>
     * <ul>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_PARENT}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_CHILD}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_SOURCE}</li>
     * <li>{@link EcmEngineConstants#ECMENGINE_ASSOC_TYPE_TARGET}</li>
     * </ul>
     */
    public void setAssociationType(String associationType) {
	this.associationType = associationType;
    }

    /**
     * Restituisce la dimesione della pagina da ricercare.
     */
    public int getPageSize() {
	return pageSize;
    }

    /**
     * Imposta la dimesione della pagina da ricercare.
     */
    public void setPageSize(int pageSize) {
	this.pageSize = pageSize;
    }

    /**
     * Restituisce il numero di pagina da ricercare.
     */
    public int getPageIndex() {
	return pageIndex;
    }

    /**
     * Imposta il numero di pagina da ricercare.
     * 
     * @param pageIndex
     */
    public void setPageIndex(int pageIndex) {
	this.pageIndex = pageIndex;
    }

    /**
     * Resituisce i tipi di associazioni da ricercare.
     */
    public String[] getFilterType() {
	return filterType;
    }

    /**
     * Imposta i tipi di associazioni da ricercare.
     */
    public void setFilterType(String[] filterType) {
	this.filterType = filterType;
    }
}
