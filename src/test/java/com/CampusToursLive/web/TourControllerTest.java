package com.CampusToursLive.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.CampusToursLive.domain.tour.TourDiscoveryService;
import com.CampusToursLive.domain.tour.TourDiscoverySort;
import com.CampusToursLive.error.NotFoundException;
import com.CampusToursLive.web.dto.TourDetailResponse;
import com.CampusToursLive.web.dto.TourSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = TourController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            OAuth2ResourceServerAutoConfiguration.class
        })
class TourControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private TourDiscoveryService discovery;

    @Test
    void list_returnsEnvelope() throws Exception {
        TourSummaryResponse row =
                new TourSummaryResponse(
                        "t1",
                        "Campus walk",
                        "campus-walk",
                        "GENERAL_CAMPUS",
                        "u1",
                        "North Coast University",
                        "g1",
                        "Maya Chen",
                        "Computer Science",
                        "BS",
                        2023,
                        60,
                        4200L,
                        "USD",
                        4.5,
                        12,
                        List.of("en-US"),
                        List.of("Q_AND_A"),
                        true);
        when(discovery.list(null, null, "", TourDiscoverySort.RECOMMENDED, 0, 20))
                .thenReturn(new PageImpl<>(List.of(row)));

        mvc.perform(get("/tours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value("t1"))
                .andExpect(jsonPath("$.data.items[0].guideDisplayName").value("Maya Chen"))
                .andExpect(jsonPath("$.data.items[0].guideMajor").value("Computer Science"))
                .andExpect(jsonPath("$.data.items[0].guideDegree").value("BS"))
                .andExpect(jsonPath("$.data.items[0].guideEntryYear").value(2023))
                .andExpect(jsonPath("$.data.items[0].languages[0]").value("en-US"))
                .andExpect(jsonPath("$.data.items[0].features[0]").value("Q_AND_A"))
                .andExpect(jsonPath("$.data.items[0].isNew").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.meta.requestId").exists());
    }

    @Test
    void list_passesQueryParams() throws Exception {
        UUID univId = UUID.randomUUID();
        when(discovery.list(
                        univId.toString(),
                        List.of("DORM_HOUSING"),
                        "dorm",
                        TourDiscoverySort.PRICE_ASC,
                        2,
                        10))
                .thenReturn(Page.empty());

        mvc.perform(
                        get("/tours")
                                .param("universityId", univId.toString())
                                .param("topic", "DORM_HOUSING")
                                .param("q", "dorm")
                                .param("sort", "PRICE_ASC")
                                .param("page", "2")
                                .param("limit", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void list_422_whenSortInvalid() throws Exception {
        mvc.perform(get("/tours").param("sort", "NEWEST"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void get_returnsEnvelope() throws Exception {
        UUID id = UUID.randomUUID();
        TourDetailResponse detail =
                new TourDetailResponse(
                        id.toString(),
                        "Campus walk",
                        "campus-walk",
                        "GENERAL_CAMPUS",
                        "Description",
                        List.of("en-US"),
                        "u1",
                        "North Coast University",
                        "north-coast",
                        "Arcata",
                        "CA",
                        "g1",
                        "Maya Chen",
                        "Bio",
                        60,
                        4200L,
                        "USD",
                        4.5,
                        12);
        when(discovery.getById(id)).thenReturn(detail);

        mvc.perform(get("/tours/{tourId}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.universitySlug").value("north-coast"));
    }

    @Test
    void get_404_whenNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        when(discovery.getById(id)).thenThrow(new NotFoundException("Tour not found"));

        mvc.perform(get("/tours/{tourId}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void get_422_whenTourIdMalformed() throws Exception {
        mvc.perform(get("/tours/{tourId}", "not-a-uuid"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }
}
