/*
 * This file is generated by jOOQ.
 */
package org.jooq.covspectrum.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row5;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.covspectrum.Indexes;
import org.jooq.covspectrum.Public;
import org.jooq.covspectrum.tables.records.SpectrumSequencePublicMutationAaRecord;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SpectrumSequencePublicMutationAa extends TableImpl<SpectrumSequencePublicMutationAaRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>public.spectrum_sequence_public_mutation_aa</code>
     */
    public static final SpectrumSequencePublicMutationAa SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA = new SpectrumSequencePublicMutationAa();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SpectrumSequencePublicMutationAaRecord> getRecordType() {
        return SpectrumSequencePublicMutationAaRecord.class;
    }

    /**
     * The column <code>public.spectrum_sequence_public_mutation_aa.sequence_name</code>.
     */
    public final TableField<SpectrumSequencePublicMutationAaRecord, String> SEQUENCE_NAME = createField(DSL.name("sequence_name"), SQLDataType.CLOB, this, "");

    /**
     * The column <code>public.spectrum_sequence_public_mutation_aa.aa_mutation</code>.
     */
    public final TableField<SpectrumSequencePublicMutationAaRecord, String> AA_MUTATION = createField(DSL.name("aa_mutation"), SQLDataType.CLOB, this, "");

    /**
     * The column <code>public.spectrum_sequence_public_mutation_aa.aa_mutation_gene</code>.
     */
    public final TableField<SpectrumSequencePublicMutationAaRecord, String> AA_MUTATION_GENE = createField(DSL.name("aa_mutation_gene"), SQLDataType.CLOB, this, "");

    /**
     * The column <code>public.spectrum_sequence_public_mutation_aa.aa_mutation_position</code>.
     */
    public final TableField<SpectrumSequencePublicMutationAaRecord, Integer> AA_MUTATION_POSITION = createField(DSL.name("aa_mutation_position"), SQLDataType.INTEGER, this, "");

    /**
     * The column <code>public.spectrum_sequence_public_mutation_aa.aa_mutation_base</code>.
     */
    public final TableField<SpectrumSequencePublicMutationAaRecord, String> AA_MUTATION_BASE = createField(DSL.name("aa_mutation_base"), SQLDataType.CLOB, this, "");

    private SpectrumSequencePublicMutationAa(Name alias, Table<SpectrumSequencePublicMutationAaRecord> aliased) {
        this(alias, aliased, null);
    }

    private SpectrumSequencePublicMutationAa(Name alias, Table<SpectrumSequencePublicMutationAaRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.materializedView());
    }

    /**
     * Create an aliased <code>public.spectrum_sequence_public_mutation_aa</code> table reference
     */
    public SpectrumSequencePublicMutationAa(String alias) {
        this(DSL.name(alias), SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA);
    }

    /**
     * Create an aliased <code>public.spectrum_sequence_public_mutation_aa</code> table reference
     */
    public SpectrumSequencePublicMutationAa(Name alias) {
        this(alias, SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA);
    }

    /**
     * Create a <code>public.spectrum_sequence_public_mutation_aa</code> table reference
     */
    public SpectrumSequencePublicMutationAa() {
        this(DSL.name("spectrum_sequence_public_mutation_aa"), null);
    }

    public <O extends Record> SpectrumSequencePublicMutationAa(Table<O> child, ForeignKey<O, SpectrumSequencePublicMutationAaRecord> key) {
        super(child, key, SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.SPECTRUM_SEQUENCE_PUBLIC_MUTA_AA_MUTATION_GENE_AA_MUTATION__IDX, Indexes.SPECTRUM_SEQUENCE_PUBLIC_MUTA_AA_MUTATION_GENE_AA_MUTATION_IDX1, Indexes.SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA_AA_MUTATION_IDX, Indexes.SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA_SEQUENCE_NAME_IDX, Indexes.SPECTRUM_SEQUENCE_PUBLIC_MUTATION_SEQUENCE_NAME_AA_MUTATION_IDX);
    }

    @Override
    public SpectrumSequencePublicMutationAa as(String alias) {
        return new SpectrumSequencePublicMutationAa(DSL.name(alias), this);
    }

    @Override
    public SpectrumSequencePublicMutationAa as(Name alias) {
        return new SpectrumSequencePublicMutationAa(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public SpectrumSequencePublicMutationAa rename(String name) {
        return new SpectrumSequencePublicMutationAa(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SpectrumSequencePublicMutationAa rename(Name name) {
        return new SpectrumSequencePublicMutationAa(name, null);
    }

    // -------------------------------------------------------------------------
    // Row5 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row5<String, String, String, Integer, String> fieldsRow() {
        return (Row5) super.fieldsRow();
    }
}
