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
 * DTO che contiene alcune informazioni applicative legate all'ambiente.
 * 
 * @author DoQui
 */
public class SystemInfo extends MtomEngineDto {
    private static final long serialVersionUID = -320675552299972354L;
    private String ecmengineVersion;
    private long heapSize;
    private long heapMaxSize;
    private long heapFreeSize;

    /**
     * Restituisce il valore misurato della heap size corrente.
     */
    public long getHeapSize() {
	return heapSize;
    }

    /**
     * Imposta il valore misurato della heap size corrente.
     */
    public void setHeapSize(long heapSize) {
	this.heapSize = heapSize;
    }

    /**
     * Restituisce il valore misurato della heap size massima.
     */
    public long getHeapMaxSize() {
	return heapMaxSize;
    }

    /**
     * Imposta il valore misurato della heap size massima.
     */
    public void setHeapMaxSize(long heapMaxSize) {
	this.heapMaxSize = heapMaxSize;
    }

    /**
     * Restituisce il valore misurato della heap libera.
     */
    public long getHeapFreeSize() {
	return heapFreeSize;
    }

    /**
     * Imposta il valore misurato della heap libera.
     */
    public void setHeapFreeSize(long heapFreeSize) {
	this.heapFreeSize = heapFreeSize;
    }

    /**
     * Restituisce il valore della versione della componente ECMEngine installata.
     */
    public String getEcmengineVersion() {
	return ecmengineVersion;
    }

    /**
     * Imposta il valore della versione della componente ECMEngine installata.
     */
    public void setEcmengineVersion(String ecmengineVersion) {
	this.ecmengineVersion = ecmengineVersion;
    }
}
