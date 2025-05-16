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
 * <p>
 * DTO che rappresenta una propriet&agrave; di sistema.
 * </p>
 * <p>
 * Questo DTO fornisce una coppia chiave/valore per identificare una
 * caratteristica di sistema.
 * </p>
 * 
 * @author DoQui
 */
public class SystemProperty extends MtomEngineDto {
    private static final long serialVersionUID = 3154698100923031253L;
    private String key;
    private String value;

    /**
     * Costruttore predefinito.
     */
    public SystemProperty() {
	super();
    }

    /**
     * Costruttore che prende in input la chiave ed il valore della propriet&agrave;
     * di sistema.
     * 
     * @param key   la chiave.
     * @param value il valore.
     */
    public SystemProperty(String key, String value) {
	this.key = key;
	this.value = value;
    }

    /**
     * Restituisce la chiave della propriet&abgrave; di sistema.
     */
    public String getKey() {
	return key;
    }

    /**
     * Imposta la chiave della propriet&abgrave; di sistema.
     */
    public void setKey(String key) {
	this.key = key;
    }

    /**
     * Restituisce il valore della propriet&abgrave; di sistema.
     */
    public String getValue() {
	return value;
    }

    /**
     * Imposta il valore della propriet&abgrave; di sistema.
     */
    public void setValue(String value) {
	this.value = value;
    }
}
