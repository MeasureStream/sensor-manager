package com.polito.tesi.measuremanager.repositories

import com.polito.tesi.measuremanager.entities.TemplateKind
import com.polito.tesi.measuremanager.entities.TemplateRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface TemplateRepository : JpaRepository<TemplateRecord, Long> {

    fun findByKindAndKeyIdAndMajorAndMinorAndPatch(
            kind: TemplateKind,
            keyId: Int,
            major: Int,
            minor: Int,
            patch: Int,
    ): TemplateRecord?

    fun findByKindAndKeyIdOrderByMajorDescMinorDescPatchDesc(
            kind: TemplateKind,
            keyId: Int,
    ): List<TemplateRecord>

    fun findByKind(kind: TemplateKind): List<TemplateRecord>
}
