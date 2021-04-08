package ch.ethz.covspectrum.service;

import ch.ethz.covspectrum.entity.api.*;
import ch.ethz.covspectrum.entity.core.DataType;
import ch.ethz.covspectrum.entity.core.*;
import ch.ethz.covspectrum.jooq.MyDSL;
import ch.ethz.covspectrum.jooq.SpectrumMetadataTable;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import org.javatuples.Pair;
import org.jooq.*;
import org.jooq.covspectrum.Tables;
import org.jooq.impl.DSL;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.threeten.extra.YearWeek;

import java.beans.PropertyVetoException;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Service
public class DatabaseService {

    public static final String BSSE = "Department of Biosystems Science and Engineering, ETH Zürich";
    public static final String HUG = "HUG, Laboratory of Virology and the Health2030 Genome Center";
    private static final ComboPooledDataSource pool = new ComboPooledDataSource();

    static {
        try {
            pool.setDriverClass("org.postgresql.Driver");
            pool.setJdbcUrl("jdbc:postgresql://" + System.getenv("COV_SPECTRUM_HOST") + ":" +
                    System.getenv("COV_SPECTRUM_PORT") + "/" + System.getenv("COV_SPECTRUM_NAME"));
            pool.setUser(System.getenv("COV_SPECTRUM_USERNAME"));
            pool.setPassword(System.getenv("COV_SPECTRUM_PASSWORD"));
        } catch (PropertyVetoException e) {
            throw new RuntimeException(e);
        }
    }


    public Connection getDatabaseConnection() throws SQLException {
        return pool.getConnection();
    }


    public DSLContext getDSLCtx(Connection connection) {
        return DSL.using(connection, SQLDialect.POSTGRES);
    }


    public List<String> getCountryNames() throws SQLException {
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            Table<?> metaTbl = getMetaTable(ctx, new SampleSelection().setUsePrivate(false));
            var statement = ctx
                    .selectDistinct(MyDSL.fCountry(metaTbl))
                    .from(metaTbl)
                    .orderBy(MyDSL.fCountry(metaTbl));
            return statement.fetch()
                    .map(Record1::value1);
        }
    }


    public int getNumberSequences(YearWeek week, String country) throws SQLException {
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            var metaTbl = getMetaTable(ctx, new SampleSelection().setUsePrivate(false));
            var statement = ctx
                    .select(DSL.count().as("count"))
                    .from(metaTbl)
                    .where(
                            MyDSL.yearWeekConstantEq(metaTbl, week),
                            MyDSL.countryConstantEq(metaTbl, country)
                    );
            return statement.fetch().get(0).value1();
        }
    }


    public List<Pair<AAMutation, Set<SampleName>>> getMutations(YearWeek yearWeek, String country) throws SQLException {
        int MINIMAL_NUMBER_OF_SAMPLES = 5;
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            var metaTbl = getMetaTable(ctx, new SampleSelection().setUsePrivate(false));
            var mutTbl = getMutTable(ctx, false);
            var statement = ctx
                    .select(
                            MyDSL.fAaMutation(mutTbl).as("mutation"),
                            DSL.groupConcat(MyDSL.fSequenceName(metaTbl)).separator(",").as("strains")
                    )
                    .from(MyDSL.metaJoinMut(metaTbl, mutTbl))
                    .where(
                            MyDSL.yearWeekConstantEq(metaTbl, yearWeek),
                            MyDSL.countryConstantEq(metaTbl, country)
                    )
                    .groupBy(MyDSL.fAaMutation(mutTbl))
                    .having(DSL.count().ge(MINIMAL_NUMBER_OF_SAMPLES));
            return statement.fetch()
                    .map(r -> new Pair<>(
                            new AAMutation(r.value1()),
                            Arrays.stream(r.value2().split(","))
                                    .map(SampleName::new).collect(Collectors.toSet())
                    ));
        }
    }


    public List<Distribution<LocalDate, CountAndProportionWithCI>> getDailyTimeDistribution(
            Variant variant,
            String region,
            String country,
            float matchPercentage,
            DataType dataType,
            LocalDate fromDate,
            LocalDate endDate
    ) throws SQLException {
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(false).setVariant(variant).setMatchPercentage(matchPercentage)
                .setRegion(region).setCountry(country)
                .setDateFrom(fromDate).setDateTo(endDate).setDataType(dataType);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            Table<?> matchedSequences = getMetaTable(ctx, selection);
            Table<?> metaTbl = getMetaTable(ctx, new SampleSelection()
                    .setUsePrivate(false).setRegion(region).setCountry(country)
                    .setDateFrom(fromDate).setDateTo(endDate).setDataType(dataType));
            Table<?> countPerDate = ctx
                    .select(
                            MyDSL.fDate(metaTbl),
                            DSL.count().as("count")
                    )
                    .from(metaTbl)
                    .groupBy(MyDSL.fDate(metaTbl))
                    .asTable();
            var statement = ctx
                    .select(
                            MyDSL.fDate(countPerDate),
                            DSL.sum(
                                    DSL.when(MyDSL.fSequenceName(matchedSequences).isNotNull(), 1)
                                    .otherwise(0)
                            ).cast(Integer.class).as("count"),
                            MyDSL.fCount(countPerDate).as("total")
                    )
                    .from(countPerDate.leftJoin(matchedSequences).on(
                            MyDSL.fDate(countPerDate).eq(MyDSL.fDate(matchedSequences))))
                    .groupBy(
                            MyDSL.fDate(countPerDate),
                            MyDSL.fCount(countPerDate)
                    )
                    .orderBy(MyDSL.fDate(countPerDate));
            return statement.fetch()
                    .map(r -> new Distribution<>(
                            r.value1(),
                            CountAndProportionWithCI.fromWilsonCI(r.value2(), r.value3())
                    ));
        }
    }


    public List<Distribution<YearWeek, CountAndProportionWithCI>> getWeeklyTimeDistribution(
            Variant variant,
            String country,
            float matchPercentage,
            DataType dataType
    ) throws SQLException {
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(false).setVariant(variant).setMatchPercentage(matchPercentage)
                .setCountry(country).setDataType(dataType);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            var sequences = getMetaTable(ctx, selection);
            Table<?> metaTbl = getMetaTable(ctx, new SampleSelection().setUsePrivate(false).setDataType(dataType));
            Table<?> countPerYearWeek = ctx
                    .select(
                            MyDSL.extractIsoYear(MyDSL.fDate(metaTbl)).as("year"),
                            DSL.extract(MyDSL.fDate(metaTbl), DatePart.WEEK).as("week"),
                            DSL.count().as("count")
                    )
                    .from(metaTbl)
                    .where(
                            MyDSL.fCountry(metaTbl).eq(country),
                            MyDSL.fDate(metaTbl).isNotNull()
                    )
                    .groupBy(MyDSL.extractIsoYear(MyDSL.fDate(metaTbl)), DSL.extract(MyDSL.fDate(metaTbl), DatePart.WEEK))
                    .asTable();
            var joined = sequences.join(countPerYearWeek).on(
                    MyDSL.extractIsoYear(MyDSL.fDate(sequences))
                            .eq(countPerYearWeek.field("year", Integer.class)),
                    DSL.extract(MyDSL.fDate(sequences), DatePart.WEEK)
                            .eq(countPerYearWeek.field("week", Integer.class))
            );
            var statement = ctx
                    .select(
                            MyDSL.extractIsoYear(MyDSL.fDate(sequences)).as("year"),
                            DSL.extract(MyDSL.fDate(sequences), DatePart.WEEK).as("week"),
                            DSL.count().as("count"),
                            MyDSL.fCount(countPerYearWeek).as("total")
                    )
                    .from(joined)
                    .groupBy(
                            MyDSL.extractIsoYear(MyDSL.fDate(sequences)),
                            DSL.extract(MyDSL.fDate(sequences), DatePart.WEEK),
                            MyDSL.fCount(countPerYearWeek)
                    );
            return statement.fetch()
                    .map(r -> new Distribution<>(
                            YearWeek.of(r.value1(), r.value2()),
                            CountAndProportionWithCI.fromWilsonCI(r.value3(), r.value4())
                    ));
        }
    }


    public List<Distribution<String, CountAndProportionWithCI>> getAgeDistribution(
            Variant variant,
            String country,
            float matchPercentage,
            boolean usePrivateVersion,
            DataType dataType
    ) throws SQLException {
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(usePrivateVersion).setVariant(variant).setMatchPercentage(matchPercentage)
                .setCountry(country).setDataType(dataType);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            Table<?> metaTbl = getMetaTable(ctx,
                    new SampleSelection().setUsePrivate(usePrivateVersion).setDataType(dataType));
            var sequences = getMetaTable(ctx, selection).as("meta");
            var groupedByAgeGroup = ctx
                    .select(MyDSL.fAgeGroup(sequences), DSL.count().as("count"))
                    .from(sequences)
                    .groupBy(MyDSL.fAgeGroup(sequences))
                    .asTable("groupedByAgeGroup");
            var countPerAgeGroup = ctx
                    .select(MyDSL.fAgeGroup(metaTbl), DSL.count().as("count"))
                    .from(metaTbl)
                    .where(
                            MyDSL.countryConstantEq(metaTbl, country),
                            MyDSL.fAgeGroup(metaTbl).isNotNull()
                    )
                    .groupBy(MyDSL.fAgeGroup(metaTbl))
                    .asTable("countPerAgeGroup");
            var statement = ctx
                    .select(
                            MyDSL.fAgeGroup(countPerAgeGroup),
                            DSL.coalesce(MyDSL.fCount(groupedByAgeGroup), 0).as("count"),
                            MyDSL.fCount(countPerAgeGroup).as("total")
                    )
                    .from(countPerAgeGroup.leftJoin(groupedByAgeGroup)
                            .on(MyDSL.fAgeGroup(countPerAgeGroup).eq(MyDSL.fAgeGroup(groupedByAgeGroup))));
            return statement.fetch()
                    .map(r -> new Distribution<>(
                            r.value1(),
                            CountAndProportionWithCI.fromWilsonCI(r.value2(), r.value3())
                    ));
        }
    }


    public List<Distribution<WeekAndCountry, CountAndProportionWithCI>> getInternationalTimeDistribution(
            Variant variant,
            float matchPercentage
    ) throws SQLException {
        List<String> mutations = variant.getMutations().stream()
                .map(AAMutation::getMutationCode)
                .collect(Collectors.toList());
        String sql = """
            select
              x.country,
              extract(isoyear from x.date) as year,
              extract(week from x.date) as week,
              count(*) as count,
              y.count as total
            from
              (
                select
                  s.country,
                  s.sequence_name,
                  s.date
                from
                  (
                    select m.sequence_name
                    from spectrum_sequence_public_mutation_aa m
                    where m.aa_mutation = any(?::text[])
                    group by m.sequence_name
                    having count(*) >= ?
                  ) m
                  join spectrum_sequence_public_meta s on s.sequence_name = m.sequence_name
              ) x
              join (
                select
                  s.country,
                  extract(isoyear from s.date) as year,
                  extract(week from s.date) as week,
                  count(*) as count
                from spectrum_sequence_public_meta s
                group by
                  s.country,
                  extract(isoyear from s.date),
                  extract(week from s.date)
              ) y on x.country = y.country
                       and extract(isoyear from x.date) = y.year
                       and extract(week from x.date) = y.week
            group by
              x.country,
              extract(isoyear from x.date),
              extract(week from x.date),
              y.count;
        """;
        try (Connection conn = getDatabaseConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setArray(1, conn.createArrayOf("text", mutations.toArray()));
            statement.setFloat(2, mutations.size() * matchPercentage);
            try (ResultSet rs = statement.executeQuery()) {
                List<Distribution<WeekAndCountry, CountAndProportionWithCI>> result = new ArrayList<>();
                while (rs.next()) {
                    Distribution<WeekAndCountry, CountAndProportionWithCI> d = new Distribution<>(
                            new WeekAndCountry(
                                    YearWeek.of(rs.getInt("year"), rs.getInt("week")),
                                    rs.getString("country")
                            ),
                            CountAndProportionWithCI.fromWilsonCI(rs.getInt("count"), rs.getInt("total"))
                    );
                    result.add(d);
                }
                return result;
            }
        }
    }


    public List<SampleFull> getSamples(
            Variant variant,
            float matchPercentage,
            boolean usePrivateVersion,
            DataType dataType
    ) throws SQLException {
        return getSamples(variant, null, matchPercentage, usePrivateVersion, dataType);
    }


    public List<SampleFull> getSamples(
            Variant variant,
            String country,
            float matchPercentage,
            boolean usePrivateVersion,
            DataType dataType
    ) throws SQLException {
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(usePrivateVersion).setVariant(variant).setMatchPercentage(matchPercentage)
                .setCountry(country).setDataType(dataType);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            Table<?> mutTbl = getMutTable(ctx, usePrivateVersion);
            var sequences = getMetaTable(ctx, selection).as("meta");
            var statement = ctx
                    .select(
                            MyDSL.fSequenceName(sequences),
                            MyDSL.fDate(sequences),
                            MyDSL.fRegion(sequences),
                            MyDSL.fCountry(sequences),
                            MyDSL.fDivision(sequences),
                            MyDSL.fLocation(sequences),
                            MyDSL.fZipCode(sequences),
                            MyDSL.fHost(sequences),
                            MyDSL.fAge(sequences),
                            MyDSL.fSex(sequences),
                            MyDSL.fSubmittingLab(sequences),
                            MyDSL.fOriginatingLab(sequences),
                            MyDSL.fAgeGroup(sequences),
                            DSL.groupConcat(MyDSL.fAaMutation(mutTbl)).separator(",").as("mutations")
                    )
                    .from(MyDSL.metaJoinMut(sequences, mutTbl))
                    .groupBy(
                            MyDSL.fSequenceName(sequences),
                            MyDSL.fDate(sequences),
                            MyDSL.fRegion(sequences),
                            MyDSL.fCountry(sequences),
                            MyDSL.fDivision(sequences),
                            MyDSL.fLocation(sequences),
                            MyDSL.fZipCode(sequences),
                            MyDSL.fHost(sequences),
                            MyDSL.fAge(sequences),
                            MyDSL.fSex(sequences),
                            MyDSL.fSubmittingLab(sequences),
                            MyDSL.fOriginatingLab(sequences),
                            MyDSL.fAgeGroup(sequences)
                    );
            List<SampleFull> result = new ArrayList<>();
            for (var r : statement.fetch()) {
                List<AAMutation> ms = Arrays.stream(r.get("mutations", String.class).split(","))
                        .map(AAMutation::new).collect(Collectors.toList());
                SamplePrivateMetadata privateMetadata = null;
                if (usePrivateVersion) {
                    privateMetadata = new SamplePrivateMetadata(
                            r.get("country", String.class),
                            r.get("division", String.class),
                            r.get("location", String.class),
                            r.get("zip_code", String.class),
                            r.get("host", String.class),
                            r.get("age", Integer.class),
                            r.get("sex", String.class),
                            r.get("submitting_lab", String.class),
                            r.get("originating_lab", String.class)
                    );
                }
                if (!usePrivateVersion && !BSSE.equals(r.get("submitting_lab", String.class))) {
                    ms = null;
                }
                SampleFull s = new SampleFull(
                        r.get("sequence_name", String.class),
                        r.get("country", String.class),
                        r.get("date", LocalDate.class),
                        ms,
                        privateMetadata
                );
                result.add(s);
            }
            return result;
        }
    }


    public List<WeightedSample> getSamples2(
            String region,
            String country,
            String mutations,
            float matchPercentage,
            DataType dataType,
            LocalDate dateFrom,
            LocalDate dateTo,
            boolean usePrivateVersion
    ) throws SQLException {
        Variant variant = null;
        if (mutations != null) {
            Set<AAMutation> aaMutations = Arrays.stream(mutations.split(","))
                    .map(AAMutation::new)
                    .collect(Collectors.toSet());
            variant = new Variant(aaMutations);
        }
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(usePrivateVersion).setVariant(variant).setMatchPercentage(matchPercentage)
                .setRegion(region).setCountry(country).setDataType(dataType)
                .setDateFrom(dateFrom).setDateTo(dateTo);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            var samples = getMetaTable(ctx, selection).as("meta");
            var statement = ctx
                    .select(
                            MyDSL.fDate(samples),
                            MyDSL.fRegion(samples),
                            MyDSL.fCountry(samples),
                            MyDSL.fDivision(samples),
                            MyDSL.fZipCode(samples),
                            MyDSL.fAgeGroup(samples),
                            MyDSL.fSex(samples),
                            MyDSL.fHospitalized(samples),
                            MyDSL.fDeceased(samples),
                            DSL.count().as("count")
                    )
                    .from(samples)
                    .groupBy(
                            MyDSL.fDate(samples),
                            MyDSL.fRegion(samples),
                            MyDSL.fCountry(samples),
                            MyDSL.fDivision(samples),
                            MyDSL.fZipCode(samples),
                            MyDSL.fAgeGroup(samples),
                            MyDSL.fSex(samples),
                            MyDSL.fHospitalized(samples),
                            MyDSL.fDeceased(samples)
                    );
            List<WeightedSample> results = statement.fetch()
                    .map(r -> new WeightedSample(
                            r.get("date", LocalDate.class),
                            r.get("region", String.class),
                            r.get("country", String.class),
                            r.get("division", String.class),
                            r.get("zip_code", String.class),
                            r.get("age_group", String.class),
                            r.get("sex", String.class),
                            r.get("hospitalized", Boolean.class),
                            r.get("deceased", Boolean.class),
                            r.get("count", Integer.class)
                    ));
            incrementSampleUsageStatistics(selection);
            return results;
        }
    }


    public List<SampleSequence> getSampleSequences(
            List<SampleName> sampleNames,
            boolean usePrivateVersion
    ) throws SQLException {
        // If the sample name begins with "UNRELEASED_ETHZ_", the sequence has to be looked up in consensus_sequence,
        // otherwise, it is in gisaid_sequence.
        List<Integer> ethids = new ArrayList<>();
        List<String> gisaid_epi_isls = new ArrayList<>();
        for (SampleName sampleName : sampleNames) {
            String s = sampleName.getName();
            if (s.startsWith("UNRELEASED_ETHZ_")) {
                ethids.add(Integer.parseInt(s.substring(16)));
            } else {
                gisaid_epi_isls.add(s);
            }
        }

        String sql;
        if (usePrivateVersion) {
            sql = """
            select
              'UNRELEASED_ETHZ_' || cs.ethid as sample_name,
              cs.seq as sequence
            from consensus_sequence cs
            where ethid = any(?::int[])
            union all
            select
              gs.gisaid_epi_isl as sample_name,
              gs.original_seq as sequence
            from gisaid_sequence gs
            where gisaid_epi_isl = any(?::text[]);
        """;
        } else {
            sql = """
            select
              'UNRELEASED_ETHZ_' || cs.ethid as sample_name,
              cs.seq as sequence
            from consensus_sequence cs
            where ethid = any(?::int[])
            union all
            select
              gs.gisaid_epi_isl as sample_name,
              gs.original_seq as sequence
            from gisaid_sequence gs
            where
                gisaid_epi_isl = any(?::text[])
                and submitting_lab = 'Department of Biosystems Science and Engineering, ETH Zürich';
        """;
        }
        try (Connection conn = getDatabaseConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setArray(1, conn.createArrayOf("int", ethids.toArray()));
            statement.setArray(2, conn.createArrayOf("text", gisaid_epi_isls.toArray()));
            try (ResultSet rs = statement.executeQuery()) {
                List<SampleSequence> result = new ArrayList<>();
                while (rs.next()) {
                    SampleSequence s = new SampleSequence(
                            rs.getString("sample_name"),
                            rs.getString("sequence")
                    );
                    result.add(s);
                }
                return result;
            }
        }
    }


    public List<Distribution<WeekAndZipCode, Count>> getPrivateTimeZipCodeDistributionOfCH(
            Variant variant,
            float matchPercentage,
            DataType dataType
    ) throws SQLException {
        SampleSelection selection = new SampleSelection()
                .setUsePrivate(true).setVariant(variant).setMatchPercentage(matchPercentage)
                .setCountry("Switzerland").setDataType(dataType);
        try (Connection conn = getDatabaseConnection()) {
            DSLContext ctx = getDSLCtx(conn);
            var sequences = getMetaTable(ctx, selection).as("meta");
            var statement = ctx
                    .select(
                            MyDSL.extractIsoYear(MyDSL.fDate(sequences)).as("year"),
                            DSL.extract(MyDSL.fDate(sequences), DatePart.WEEK).as("week"),
                            MyDSL.fZipCode(sequences).as("zip_code"),
                            DSL.count().as("count")
                    )
                    .from(sequences)
                    .where(MyDSL.fZipCode(sequences).isNotNull())
                    .groupBy(
                            MyDSL.fZipCode(sequences),
                            MyDSL.extractIsoYear(MyDSL.fDate(sequences)),
                            DSL.extract(MyDSL.fDate(sequences), DatePart.WEEK)
                    );
            return statement.fetch()
                    .map(r -> new Distribution<>(
                            new WeekAndZipCode(
                                    YearWeek.of(r.value1(), r.value2()),
                                    r.value3()
                            ),
                            new Count(r.value4())
                    ));
        }
    }


    public List<Distribution<LocalDate, CasesAndSequences>> getTimeIntensityDistribution(
            String region,
            String country,
            DataType dataType
    ) throws SQLException {
        String sql = """
            select
              date,
              region,
              country,
              cases,
              sequenced,
              sequenced_surveillance
            from spectrum_sequence_intensity
            where date is not null and
        """;
        if (country == null && region == null) {
            sql += " country is null and region is null\n";
        } else if (country == null) {
            sql += " country is null and region = ?\n";
        } else {
            sql += " country = ?\n";
        }
        sql += "order by date;";
        try (Connection conn = getDatabaseConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            if (country == null && region == null) {
                // No params
            } else if (country == null) {
                statement.setString(1, region);
            } else {
                statement.setString(1, country);
            }
            try (ResultSet rs = statement.executeQuery()) {
                List<Distribution<LocalDate, CasesAndSequences>> result = new ArrayList<>();
                while (rs.next()) {
                    Distribution<LocalDate, CasesAndSequences> d = new Distribution<>(
                            rs.getDate("date").toLocalDate(),
                            new CasesAndSequences(
                                    rs.getInt("cases"),
                                    rs.getInt(dataType == DataType.SURVEILLANCE ? "sequenced_surveillance" : "sequenced")
                            )
                    );
                    result.add(d);
                }
                return result;
            }
        }
    }


    public String getPrecomputedInterestingVariants(
            String country,
            @Nullable DataType dataType
    ) throws SQLException {
        // TODO Use datatype
        String sql = """
            select result
            from spectrum_new_interesting_variant
            where country = ?
            order by insertion_timestamp desc
            limit 1;
        """;
        try (Connection conn = getDatabaseConnection();
             PreparedStatement statement = conn.prepareStatement(sql)) {
            statement.setString(1, country);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("result");
                } else {
                    return null;
                }
            }
        }
    }


    private Table<?> getMetaTable(DSLContext ctx, SampleSelection selection) {
        boolean usePrivate = selection.isUsePrivate();
        SpectrumMetadataTable table = !usePrivate ? SpectrumMetadataTable.PUBLIC : SpectrumMetadataTable.PRIVATE;
        Table<?> metaTbl = ctx
                .select(
                        table.asterisk(),
                        DSL
                                .when(table.AGE.lt(10), "0-9")
                                .when(table.AGE.lt(20), "10-19")
                                .when(table.AGE.lt(30), "20-29")
                                .when(table.AGE.lt(40), "30-39")
                                .when(table.AGE.lt(50), "40-49")
                                .when(table.AGE.lt(60), "50-59")
                                .when(table.AGE.lt(70), "60-69")
                                .when(table.AGE.lt(80), "70-79")
                                .when(table.AGE.ge(80), "80+").as("age_group")
                )
                .from(table)
                .where(table.DATE.isNotNull())
                .asTable("spectrum_metadata");

        List<Condition> conditions = new ArrayList<>();

        Table<?> mutTbl = null;
        if (selection.getVariant() != null) {
            mutTbl = getMutTable(ctx, selection.isUsePrivate());
            Condition c = DSL.falseCondition();
            for (AAMutation mutation : selection.getVariant().getMutations()) {
                c = c.or(MyDSL.aaMutationEq(mutTbl, mutation));
            }
            conditions.add(c);
        }
        if (selection.getRegion() != null) {
            conditions.add(MyDSL.fRegion(metaTbl).eq(selection.getRegion()));
        }
        if (selection.getCountry() != null) {
            conditions.add(MyDSL.countryConstantEq(metaTbl, selection.getCountry()));
        }
        if (selection.getDataType() != null) {
            if (selection.getDataType() == DataType.SURVEILLANCE) {
                conditions.add(MyDSL.fSubmittingLab(metaTbl).eq(BSSE)
                        .or(MyDSL.fSubmittingLab(metaTbl).eq(HUG)));
            }
        }
        if (selection.getDateFrom() != null) {
            conditions.add(MyDSL.fDate(metaTbl).ge(selection.getDateFrom()));
        }
        if (selection.getDateTo() != null) {
            conditions.add(MyDSL.fDate(metaTbl).le(selection.getDateTo()));
        }
        List<Field<?>> fields = Arrays.asList(
                MyDSL.fSequenceName(metaTbl),
                MyDSL.fDate(metaTbl),
                MyDSL.fRegion(metaTbl),
                MyDSL.fCountry(metaTbl),
                MyDSL.fDivision(metaTbl),
                MyDSL.fLocation(metaTbl),
                MyDSL.fZipCode(metaTbl),
                MyDSL.fHost(metaTbl),
                MyDSL.fAge(metaTbl),
                MyDSL.fSex(metaTbl),
                MyDSL.fSubmittingLab(metaTbl),
                MyDSL.fOriginatingLab(metaTbl),
                MyDSL.fHospitalized(metaTbl),
                MyDSL.fDeceased(metaTbl),
                MyDSL.fAgeGroup(metaTbl)
        );
        if (selection.getVariant() == null) {
            return ctx.
                    select(fields)
                    .from(metaTbl)
                    .where(conditions)
                    .asTable();
        } else {
            return ctx.
                    select(fields)
                    .from(MyDSL.metaJoinMut(metaTbl, mutTbl))
                    .where(conditions)
                    .groupBy(fields)
                    .having(DSL.count().ge((int) Math.ceil(
                            selection.getMatchPercentage() * selection.getVariant().getMutations().size())))
                    .asTable();
        }
    }


    private Table<?> getMutTable(DSLContext ctx, boolean usePrivate) {
        return ctx.select().from(Tables.SPECTRUM_SEQUENCE_PUBLIC_MUTATION_AA).asTable();
    }


    private void incrementSampleUsageStatistics(SampleSelection selection) throws SQLException {
        String sql = """
            insert into spectrum_api_usage_sample as s (
                isoyear, isoweek, usage_count,
                private_version, region, country, mutations, match_percentage,
                data_type, date_from, date_to
            )
            values (
                extract(isoyear from current_date), extract(week from current_date), 1,
                ?, ?, ?, ?, ?, ?, ?, ?
            )
            on conflict on constraint spectrum_api_usage_sample_unique_constraint
              do update
              set usage_count = s.usage_count + 1;
        """;
        try (Connection conn = getDatabaseConnection()) {
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                statement.setBoolean(1, selection.isUsePrivate());
                statement.setString(2, Objects.requireNonNullElse(selection.getRegion(), ""));
                statement.setString(3, Objects.requireNonNullElse(selection.getCountry(), ""));
                String mutationsString = "";
                if (selection.getVariant() != null) {
                    mutationsString = selection.getVariant().getMutations().stream()
                            .map(AAMutation::decode)
                            .map(AAMutationDecoded::toString)
                            .sorted()
                            .collect(Collectors.joining(","));
                }
                statement.setString(4, mutationsString);
                statement.setFloat(5, selection.getMatchPercentage());
                String dataType = "";
                if (selection.getDataType() != null) {
                    dataType = selection.getDataType().toString();
                }
                statement.setString(6, dataType);
                statement.setDate(7, Date.valueOf(Objects.requireNonNullElse(selection.getDateFrom(),
                        LocalDate.of(1990, 1, 1))));
                statement.setDate(8, Date.valueOf(Objects.requireNonNullElse(selection.getDateFrom(),
                        LocalDate.of(1990, 1, 1))));
                statement.execute();
            }
        }
    }
}
