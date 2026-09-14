package it.unibo.cas.eventmanagement.repositories;

import it.unibo.cas.eventmanagement.models.entities.AnalysisStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalysisStatsRepository extends JpaRepository<AnalysisStats, Long> {
    List<AnalysisStats> findAllByAreaId(String areaId);

    List<AnalysisStats> findAllByAreaIdOrderByTs(String areaId);

    @Query("SELECT a.estimatedPeople FROM AnalysisStats a WHERE a.areaId = :areaId ORDER BY a.ts")
    List<Long> findEstimatedPeopleByAreaIdOrderByTs(@Param("areaId") String areaId);

    @Query("SELECT a.density FROM AnalysisStats a WHERE a.areaId = :areaId ORDER BY a.ts")
    List<Double> findDensityByAreaIdOrderByTs(@Param("areaId") String areaId);
    
    @Query("SELECT a FROM AnalysisStats a WHERE a.areaId = :areaId AND a.ts >= :after ORDER BY a.ts")
    List<AnalysisStats> findRecentByAreaId(@Param("areaId") String areaId, @Param("after") java.time.OffsetDateTime after);

    AnalysisStats findFirstByAreaIdOrderByTsDesc(String areaId);
}