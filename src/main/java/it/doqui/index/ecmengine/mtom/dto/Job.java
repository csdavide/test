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

import java.util.Date;

/**
 * DTO che rappresenta un job, ovvero una richiesta asincrona di esecuzione di
 * un servizio.
 * 
 * @author DoQui
 */
public class Job extends MtomEngineDto {
    /**
     * 
     */
    private static final long serialVersionUID = -8768299449756396193L;

    /**
     * Indica che il job &egrave; pronto ad essere eseguito.
     */
    public static final String STATUS_READY = "READY";

    /**
     * Indica che il job &egrave; in esecuzione.
     */
    public static final String STATUS_RUNNING = "RUNNING";

    /**
     * Indica che il job &egrave; terminato correttamente.
     */
    public static final String STATUS_FINISHED = "FINISHED";

    /**
     * Indica che il job &egrave; terminato con un'eccezione.
     */
    public static final String STATUS_ERROR = "ERROR";

    private String name;

    public Job() {
	super();
    }

    public Job(String name, String status, Date created, Date updated, String message) {
	super();
	this.name = name;
	this.status = status;
	this.created = created;
	this.updated = updated;
	this.message = message;
    }

    private String status;
    private Date created;
    private Date updated;
    private String message;

    /**
     * Restituisce il nome del job.
     */
    public String getName() {
	return name;
    }

    /**
     * Imposta il nome del job.
     */
    public void setName(String name) {
	this.name = name;
    }

    /**
     * Restituisce lo stato del job, che pu&ograve; essere READY, RUNNING, FINISHED,
     * ERROR.
     */
    public String getStatus() {
	return status;
    }

    /**
     * Imposta lo stato del job, che pu&ograve; essere READY, RUNNING, FINISHED,
     * ERROR.
     */
    public void setStatus(String status) {
	this.status = status;
    }

    /**
     * Restituisce la data di creazione del job.
     */
    public Date getCreated() {
	return created;
    }

    /**
     * Imposta la data di creazione del job.
     */
    public void setCreated(Date created) {
	this.created = created;
    }

    /**
     * Restituisce la data di ultimo aggiornamento del job.
     */
    public Date getUpdated() {
	return updated;
    }

    /**
     * Imposta la data di ultimo aggiornamento del job.
     */
    public void setUpdated(Date updated) {
	this.updated = updated;
    }

    /**
     * Restituisce il messaggio del job, valorizzato specialmente in caso di errore.
     */
    public String getMessage() {
	return message;
    }

    /**
     * Imposta il messaggio del job, valorizzato specialmente in caso di errore.
     */
    public void setMessage(String message) {
	this.message = message;
    }
}
