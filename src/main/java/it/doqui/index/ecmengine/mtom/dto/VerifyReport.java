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
 * DTO che rappresenta un rapporto di verifica della firma digitale di un
 * documento.
 * 
 * @author DoQui
 */
public class VerifyReport extends MtomEngineDto {
    private static final long serialVersionUID = -8958460163975305571L;
    private String uid[];
    private Signature[] signature;
    private Date date;
    private int errorCode;
    private VerifyReport child;
    private byte[] data;
    private int tipoFirma;

    /*
     * LBS 28/04/2010 attributi aggiunti per l'algoritmo di verifica firma acta
     */
    private int conformitaParametriInput;
    private int formatoFirma;
    private int numCertificatiFirma;
    private int numCertificatiMarca;

    /**
     * Restituisce le firme del file.
     */
    public Signature[] getSignature() {
	return signature;
    }

    /**
     * Imposta le firme del file.
     */
    public void setSignature(Signature[] signature) {
	this.signature = signature;
    }

    /**
     * Restituisce la data in cui &egrave; stato chiesto il rapporto di verifica.
     */
    public Date getDate() {
	return date;
    }

    /**
     * Imposta la data in cui &egrave; stato chiesto il rapporto di verifica.
     */
    public void setDate(Date date) {
	this.date = date;
    }

    /**
     * Restituisce l'eventuale codice di errore.
     */
    public int getErrorCode() {
	return errorCode;
    }

    /**
     * Imposta l'eventuale codice di errore.
     */
    public void setErrorCode(int errorCode) {
	this.errorCode = errorCode;
    }

    /**
     * Restituisce l'eventuale rapporto di verifica figlio.
     */
    public VerifyReport getChild() {
	return child;
    }

    /**
     * Imposta l'eventuale rapporto di verifica figlio.
     */
    public void setChild(VerifyReport child) {
	this.child = child;
    }

    /**
     * Restituisce l'eventuale UID del file.
     */
    public String[] getUid() {
	return uid;
    }

    /**
     * Imposta l'eventuale UID del file.
     */
    public void setUid(String[] uid) {
	this.uid = uid;
    }

    /**
     * Restituisce la tipologia di firma o marca temporale: {@code 0} in caso di
     * marca temporale, {@code 1} in caso di firma semplice, {@code 2} in caso di
     * firma multipla parallela, {@code 3} in caso di firma multipla controfirma,
     * {@code 4} in caso di firma multipla a catena.
     */
    public int getTipoFirma() {
	return tipoFirma;
    }

    /**
     * Imposta la tipologia di firma o marca temporale: {@code 0} in caso di marca
     * temporale, {@code 1} in caso di firma semplice, {@code 2} in caso di firma
     * multipla parallela, {@code 3} in caso di firma multipla controfirma,
     * {@code 4} in caso di firma multipla a catena.
     */
    public void setTipoFirma(int tipoFirma) {
	this.tipoFirma = tipoFirma;
    }

    /**
     * Restituisce la conformita dei parametri di input: {@code 0} NON OK, {@code 1}
     * OK.
     */
    public int getConformitaParametriInput() {
	return conformitaParametriInput;
    }

    /**
     * Imposta la conformita dei parametri di input: {@code 0} NON OK, {@code 1} OK.
     */
    public void setConformitaParametriInput(int conformitaParametriInput) {
	this.conformitaParametriInput = conformitaParametriInput;
    }

    /**
     * Restituisce il formato della firma: {@code 1} in caso di firma enveloped,
     * {@code 2} in caso di firma separata, {@code 3} in caso di marca temporale
     * InfoCert, {@code 4} in caso di marca temporale separata.
     */
    public int getFormatoFirma() {
	return formatoFirma;
    }

    /**
     * Imposta il formato della firma: {@code 1} in caso di firma enveloped,
     * {@code 2} in caso di firma separata, {@code 3} in caso di marca temporale
     * InfoCert, {@code 4} in caso di marca temporale separata.
     */
    public void setFormatoFirma(int formatoFirma) {
	this.formatoFirma = formatoFirma;
    }

    /**
     * Restituisce il numero dei certificati della firma.
     */
    public int getNumCertificatiFirma() {
	return numCertificatiFirma;
    }

    /**
     * Imposta il numero dei certificati della firma.
     */
    public void setNumCertificatiFirma(int numCertificatiFirma) {
	this.numCertificatiFirma = numCertificatiFirma;
    }

    /**
     * Restituisce il numero dei certificati della marca temporale.
     */
    public int getNumCertificatiMarca() {
	return numCertificatiMarca;
    }

    /**
     * Imposta il numero dei certificati della marca temporale.
     */
    public void setNumCertificatiMarca(int numCertificatiMarca) {
	this.numCertificatiMarca = numCertificatiMarca;
    }

    @Deprecated
    public byte[] getData() {
	return data;
    }

    @Deprecated
    public void setData(byte[] data) {
	this.data = data;
    }
}
