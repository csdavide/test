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
 * DTO astratto che rappresenta un elemento della definizione del modello dei
 * dati.
 * 
 * @author DoQui
 */
public abstract class ModelDto extends MtomEngineDto {
    private static final long serialVersionUID = -2598065913246169304L;
    private String prefixedName;
    private String title;
    private String description;

    /**
     * Restituisce il prefixed name dell'elemento.
     */
    public String getPrefixedName() {
	return prefixedName;
    }

    /**
     * Imposta il prefixed name dell'elemento.
     */
    public void setPrefixedName(String prefixedName) {
	this.prefixedName = prefixedName;
    }

    /**
     * Restituisce il titolo dell'elemento.
     */
    public String getTitle() {
	return title;
    }

    /**
     * Imposta il titolo dell'elemento.
     */
    public void setTitle(String title) {
	this.title = title;
    }

    /**
     * Restituisce la descrizione dell'elemento.
     */
    public String getDescription() {
	return description;
    }

    /**
     * Imposta la descrizione dell'elemento.
     */
    public void setDescription(String description) {
	this.description = description;
    }
}
