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
 * DTO che rappresenta una firma digitale.
 * 
 * @author DoQui
 */
public class Signature extends MtomEngineDto {
    private static final long serialVersionUID = -3559436020345877781L;
    private String nominativoFirmatario;
    private String organizzazione;
    private String ca;
    private String dipartimento;
    private String paese;

    /*
     * LBS 28/04/2010 tipoFirma non e' valido a questo livello, considerare il
     * tipoFirma di VerifyReport
     */
    private long tipoFirma;

    private long giornoFirma;
    private long oraFirma;
    private long annoFirma;
    private long meseFirma;
    private long minutiFirma;
    private long secondiFirma;
    private String serialNumber;
    private String surname;
    private String givenname;
    private String dnQualifier;
    private int errorCode;

    // Dalla versione 1.3.0 di Dosign
    private boolean timestamped;
    private String inizioValidita;
    private String fineValidita;
    private String codiceFiscale;
    private long numeroControfirme;

    // Array delle controfirme della firma
    private Signature[] signature;
    private byte[] cert;

    // Dalla versione 7.2 di ECMengine
    private Date dataOra;
    private String firmatario;

    /*
     * LBS 28/04/2010 attributi aggiunti secondo l'algoritmo di verifica firma di
     * Acta
     */
    String dataOraVerifica;
    int tipoCertificato;

    /**
     * Restituisce l'anno della firma.
     */
    public long getAnnoFirma() {
	return annoFirma;
    }

    /**
     * Imposta l'anno della firma.
     */
    public void setAnnoFirma(long annoFirma) {
	this.annoFirma = annoFirma;
    }

    /**
     * Restituisce la certification authority.
     */
    public String getCa() {
	return ca;
    }

    /**
     * Imposta la certification authority.
     */
    public void setCa(String ca) {
	this.ca = ca;
    }

    /**
     * Restituisce il dipartimento.
     */
    public String getDipartimento() {
	return dipartimento;
    }

    /**
     * Imposta il dipartimento.
     */
    public void setDipartimento(String dipartimento) {
	this.dipartimento = dipartimento;
    }

    /**
     * Restituisce il giorno della firma.
     */
    public long getGiornoFirma() {
	return giornoFirma;
    }

    /**
     * Imposta il giorno della firma.
     */
    public void setGiornoFirma(long giornoFirma) {
	this.giornoFirma = giornoFirma;
    }

    /**
     * Restituisce il mese della firma.
     */
    public long getMeseFirma() {
	return meseFirma;
    }

    /**
     * Imposta il mese della firma.
     */
    public void setMeseFirma(long meseFirma) {
	this.meseFirma = meseFirma;
    }

    /**
     * Restituisce i minuti della firma.
     */
    public long getMinutiFirma() {
	return minutiFirma;
    }

    /**
     * Imposta i minuti della firma.
     */
    public void setMinutiFirma(long minutiFirma) {
	this.minutiFirma = minutiFirma;
    }

    /**
     * Restituisce il nominativo del firmatario.
     */
    public String getNominativoFirmatario() {
	return nominativoFirmatario;
    }

    /**
     * Imposta il nominativo del firmatario.
     */
    public void setNominativoFirmatario(String nominativoFirmatario) {
	this.nominativoFirmatario = nominativoFirmatario;
    }

    /**
     * Restituisce l'ora della firma.
     */
    public long getOraFirma() {
	return oraFirma;
    }

    /**
     * Imposta l'ora della firma.
     */
    public void setOraFirma(long oraFirma) {
	this.oraFirma = oraFirma;
    }

    /**
     * Restituisce l'organizzazione del firmatario.
     */
    public String getOrganizzazione() {
	return organizzazione;
    }

    /**
     * Imposta l'organizzazione del firmatario.
     */
    public void setOrganizzazione(String organizzazione) {
	this.organizzazione = organizzazione;
    }

    /**
     * Restituisce il paese del firmatario.
     */
    public String getPaese() {
	return paese;
    }

    /**
     * Imposta il paese del firmatario.
     */
    public void setPaese(String paese) {
	this.paese = paese;
    }

    /**
     * Restituisce i secondi della firma.
     */
    public long getSecondiFirma() {
	return secondiFirma;
    }

    /**
     * Imposta i secondi della firma.
     */
    public void setSecondiFirma(long secondiFirma) {
	this.secondiFirma = secondiFirma;
    }

    /**
     * Restituisce il tipo della firma.
     */
    public long getTipoFirma() {
	return tipoFirma;
    }

    /**
     * Imposta il tipo della firma.
     */
    public void setTipoFirma(long tipoFirma) {
	this.tipoFirma = tipoFirma;
    }

    /**
     * Restituisce il serial number del firmatario.
     */
    public String getSerialNumber() {
	return serialNumber;
    }

    /**
     * Imposta il serial number del firmatario.
     */
    public void setSerialNumber(String serialNumber) {
	this.serialNumber = serialNumber;
    }

    /**
     * Restituisce il cognome del firmatario.
     */
    public String getSurname() {
	return surname;
    }

    /**
     * Imposta il cognome del firmatario.
     */
    public void setSurname(String surname) {
	this.surname = surname;
    }

    /**
     * Restituisce il nome del firmatario.
     */
    public String getGivenname() {
	return givenname;
    }

    /**
     * Imposta il nome del firmatario.
     */
    public void setGivenname(String givenname) {
	this.givenname = givenname;
    }

    /**
     * Restituisce il dnqualifier del firmatario.
     */
    public String getDnQualifier() {
	return dnQualifier;
    }

    /**
     * Imposta il dnqualifier del firmatario.
     */
    public void setDnQualifier(String dnQualifier) {
	this.dnQualifier = dnQualifier;
    }

    /**
     * Verifica se la firma &egrave; fornita di una marcatura temporale.
     */
    public boolean isTimestamped() {
	return timestamped;
    }

    /**
     * Imposta se la firma &egrave; fornita di una marcatura temporale.
     * 
     * @param timestamped
     */
    public void setTimestamped(boolean timestamped) {
	this.timestamped = timestamped;
    }

    /**
     * Restituisce la data di inizio di validit&agrave; della firma.
     */
    public String getInizioValidita() {
	return inizioValidita;
    }

    /**
     * Imposta la data di inizio di validit&agrave; della firma.
     */
    public void setInizioValidita(String inizioValidita) {
	this.inizioValidita = inizioValidita;
    }

    /**
     * Restituisce la data di fine di validit&agrave; della firma.
     */
    public String getFineValidita() {
	return fineValidita;
    }

    /**
     * Imposta la data di fine di validit&agrave; della firma.
     */
    public void setFineValidita(String fineValidita) {
	this.fineValidita = fineValidita;
    }

    /**
     * Restituisce il codice fiscale del firmatario.
     */
    public String getCodiceFiscale() {
	return codiceFiscale;
    }

    /**
     * Imposta il codice fiscale del firmatario.
     */
    public void setCodiceFiscale(String codiceFiscale) {
	this.codiceFiscale = codiceFiscale;
    }

    /**
     * Restituisce il numero di controfirme del file.
     */
    public long getNumeroControfirme() {
	return numeroControfirme;
    }

    /**
     * Imposta il numero di controfirme del file.
     */
    public void setNumeroControfirme(long numeroControfirme) {
	this.numeroControfirme = numeroControfirme;
    }

    /**
     * Restituisce le controfirme.
     */
    public Signature[] getSignature() {
	return signature;
    }

    /**
     * Imposta le controfirme.
     */
    public void setSignature(Signature[] signature) {
	this.signature = signature;
    }

    /**
     * Restituisce il certificato di firma.
     */
    public byte[] getCert() {
	return cert;
    }

    /**
     * Imposta il certificato di firma.
     */
    public void setCert(byte[] cert) {
	this.cert = cert;
    }

    /**
     * Restituisce l'eventuale codice di errore della verifica della firma.
     */
    public int getErrorCode() {
	return errorCode;
    }

    /**
     * Imposta l'eventuale codice di errore della verifica della firma.
     */
    public void setErrorCode(int errorCode) {
	this.errorCode = errorCode;
    }

    /**
     * Restiruisce la data e l'ora della firma.
     */
    public Date getDataOra() {
	return dataOra;
    }

    /**
     * Imposta la data e l'ora della firma.
     */
    public void setDataOra(Date dataOra) {
	this.dataOra = dataOra;
    }

    /**
     * Restituisce il firmatario.
     */
    public String getFirmatario() {
	return firmatario;
    }

    /**
     * Imposta il firmatario.
     */
    public void setFirmatario(String firmatario) {
	this.firmatario = firmatario;
    }

    /**
     * Restituise la data e ora del rapporto di verifica nel formato
     * {@code gg/mm/aaaa hh:mm:ss}.
     * 
     * @return data ora rapporto verifica
     */
    public String getDataOraVerifica() {
	return dataOraVerifica;
    }

    /**
     * Imposta la data e ora del rapporto di verifica nel formato
     * {@code gg/mm/aaaa hh:mm:ss}.
     */
    public void setDataOraVerifica(String dataOraVerifica) {
	this.dataOraVerifica = dataOraVerifica;
    }

    /**
     * Restituisce il tipo del certificato: {@code 1}, in caso di firma, {@code 2},
     * in caso di marca temporale;
     */
    public int getTipoCertificato() {
	return tipoCertificato;
    }

    /**
     * Imposta il tipo del certificato: {@code 1}, in caso di firma, {@code 2}, in
     * caso di marca temporale;
     */
    public void setTipoCertificato(int tipoCertificato) {
	this.tipoCertificato = tipoCertificato;
    }
}
