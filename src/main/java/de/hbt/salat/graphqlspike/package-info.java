/**
 * Spike of a GraphQL interface (#1532), stage 0 of {@code docs/ideen/graphql-konzept.md}. Experimental and
 * not fit for production: it answers the open questions of the concept (section 6.4) and is then either
 * deleted or moved into the modules.
 *
 * <p><b>Switched off by default, twice.</b>
 * <ul>
 *   <li>The schema lives in {@code classpath:graphql-spike/}, not in Spring's default {@code classpath:graphql/}.
 *   Without the profile {@code graphql-spike} the auto-configuration finds no schema and starts nothing:
 *   no endpoint, no bean. The profile sets the location ({@code application-graphql-spike.yaml}), and
 *   {@code GraphqlSpikeConfiguration} refuses to start without the profile {@code local}.</li>
 *   <li>{@code SpikeAccessFilter} lets through only the logins listed in
 *   {@code salat.graphql-spike.allowed-signs}. The list is empty in the repository and set locally through
 *   the environment ({@code SALAT_GRAPHQLSPIKE_ALLOWEDSIGNS}).</li>
 * </ul>
 *
 * <p><b>Shape.</b> One sub-package per module the spike reads ({@code employee}, {@code dailyreport}) with its
 * own schema file, cut along the import direction {@code ArchitectureTest} allows (concept 3.7). Being a module
 * of its own, the spike may call only service methods without entities in their signature (#1244) — where it
 * would need one, that is a finding for the concept (3.2), not an entry in the archunit store. No module
 * imports this package ({@code ArchitectureTest.nothingShouldAccessGraphqlSpike}). {@code probe} holds the
 * fields that make the runtime context observable; it is spike instrumentation and is not carried over.
 *
 * <p><b>Removing it:</b> delete this package, {@code src/main/resources/graphql-spike/},
 * {@code application-graphql-spike.yaml}, the dependency {@code spring-boot-starter-graphql} in {@code pom.xml},
 * the tests under {@code src/test/java/de/hbt/salat/graphqlspike/} and the rule {@code nothingShouldAccessGraphqlSpike}
 * in {@code ArchitectureTest}.
 *
 * <p><b>Taking it over:</b> each sub-package moves into its module as {@code <module>.graphql}, its schema file to
 * {@code classpath:graphql/}; {@code config} goes to {@code common.graphql}; {@code probe}, the access filter and
 * the profile are dropped. What the spike works around instead of changing the services is listed in the
 * concept, section "Ergebnisse des Spikes".
 */
package de.hbt.salat.graphqlspike;
