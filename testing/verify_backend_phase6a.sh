#!/bin/bash
set -e
B=/home/claude/foodloop-ai/backend/src/main/java; rm -rf /tmp/v/out1 /tmp/v/out2 /tmp/v/stubs; mkdir -p /tmp/v/out1 /tmp/v/out2
javac --release 17 -d /tmp/v/out1 $B/com/foodloop/util/GeoUtils.java $B/com/foodloop/entity/enums/*.java /tmp/v/Check.java && java -cp /tmp/v/out1 Check
S=/tmp/v/stubs; mkdir -p $S/jakarta/persistence $S/lombok $S/org/hibernate/annotations $S/org/hibernate/type $S/org/springframework/data/domain $S/org/springframework/data/jpa/repository $S/org/springframework/stereotype
for n in Entity Id MapsId Version; do echo "package jakarta.persistence; public @interface $n {}" > $S/jakarta/persistence/$n.java; done
echo 'package jakarta.persistence; public @interface Table { String name(); }' > $S/jakarta/persistence/Table.java
echo 'package jakarta.persistence; public @interface Column { String name() default ""; boolean updatable() default true; }' > $S/jakarta/persistence/Column.java
echo 'package jakarta.persistence; public @interface JoinColumn { String name(); boolean nullable() default true; boolean unique() default false; }' > $S/jakarta/persistence/JoinColumn.java
echo 'package jakarta.persistence; public enum FetchType { LAZY, EAGER }' > $S/jakarta/persistence/FetchType.java
echo 'package jakarta.persistence; public enum EnumType { STRING, ORDINAL }' > $S/jakarta/persistence/EnumType.java
echo 'package jakarta.persistence; public enum GenerationType { IDENTITY, AUTO }' > $S/jakarta/persistence/GenerationType.java
echo 'package jakarta.persistence; public @interface GeneratedValue { GenerationType strategy(); }' > $S/jakarta/persistence/GeneratedValue.java
echo 'package jakarta.persistence; public @interface Enumerated { EnumType value(); }' > $S/jakarta/persistence/Enumerated.java
echo 'package jakarta.persistence; public @interface ManyToOne { FetchType fetch() default FetchType.EAGER; boolean optional() default true; }' > $S/jakarta/persistence/ManyToOne.java
echo 'package jakarta.persistence; public @interface OneToOne { FetchType fetch() default FetchType.EAGER; boolean optional() default true; }' > $S/jakarta/persistence/OneToOne.java
for n in Getter Setter NoArgsConstructor; do echo "package lombok; public @interface $n {}" > $S/lombok/$n.java; done
for n in CreationTimestamp UpdateTimestamp; do echo "package org.hibernate.annotations; public @interface $n {}" > $S/org/hibernate/annotations/$n.java; done
echo 'package org.hibernate.annotations; public @interface JdbcTypeCode { int value(); }' > $S/org/hibernate/annotations/JdbcTypeCode.java
echo 'package org.hibernate.type; public class SqlTypes { public static final int JSON = 3001; }' > $S/org/hibernate/type/SqlTypes.java
echo 'package org.springframework.data.domain; public interface Page<T> {}' > $S/org/springframework/data/domain/Page.java
echo 'package org.springframework.data.domain; public interface Pageable {}' > $S/org/springframework/data/domain/Pageable.java
echo 'package org.springframework.data.jpa.repository; public interface JpaRepository<T,ID> {}' > $S/org/springframework/data/jpa/repository/JpaRepository.java
echo 'package org.springframework.stereotype; public @interface Repository {}' > $S/org/springframework/stereotype/Repository.java
javac --release 17 -d /tmp/v/out2 $(find $S -name "*.java") $(find $B/com/foodloop/entity $B/com/foodloop/repository -name "*.java")
echo "ENTITY+REPOSITORY COMPILE (stub libs): OK, classes: $(ls /tmp/v/out2/com/foodloop/entity/*.class | wc -l)"
