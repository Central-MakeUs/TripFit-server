package com.tripfit.reporter.collect;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceCodeLocatorTest {

  @TempDir Path repo;

  private void write(String relative, String content) throws Exception {
    Path file = repo.resolve("src/main/java/com/example/" + relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }

  // TripFit과 같은 모양: 여러 줄짜리 어노테이션 아래 메서드, 한 줄 위임 facade, 실제 처리 서비스.
  private void writeTripLikeSources() throws Exception {
    write(
        "TripController.java",
        """
        package com.example;

        @RestController
        @RequestMapping("/api/v1/trips")
        public class TripController {

          private final TripService tripService;

          @Operation(summary = "여행방 상세")
          @GetMapping("/{tripId}")
          @ApiResponses({
              @ApiResponse(
                  responseCode = "200",
                  useReturnTypeSchema = true)
          })
          ResponseEntity<SuccessResponse<TripDetailResponse>> getTrip(
              @AuthorizedUser UUID userId, @PathVariable UUID tripId) {
            return ResponseEntity.ok(SuccessResponse.of(tripService.getTrip(tripId, userId)));
          }

          @PostMapping
          ResponseEntity<Void> createTrip() {
            return null;
          }
        }
        """);
    write(
        "TripService.java",
        """
        package com.example;

        public class TripService {

          private final TripQueryService tripQueryService;

          public TripDetailResponse getTrip(UUID tripId, UUID userId) {
            return tripQueryService.getTrip(tripId, userId);
          }
        }
        """);
    write(
        "TripQueryService.java",
        """
        package com.example;

        public class TripQueryService {

          private final TripRepository tripRepository;

          public TripDetailResponse getTrip(UUID tripId, UUID userId) {
            Trip trip = tripRepository.findById(tripId).orElseThrow();
            List<TripMember> members = trip.getMembers();
            members.forEach(m -> m.getUser().getName());
            validate(trip, userId);
            log(trip);
            audit(trip);
            return TripDetailResponse.from(trip, members);
          }
        }
        """);
  }

  @Test
  void locate_controllerThroughFacade_returnsControllerFacadeAndRealService() throws Exception {
    writeTripLikeSources();
    SourceCodeLocator locator = new SourceCodeLocator(repo, false);

    List<SourceCodeLocator.Snippet> snippets = locator.locate("GET", "/api/v1/trips/{tripId}");

    assertThat(snippets).extracting(SourceCodeLocator.Snippet::file)
        .containsExactly(
            "src/main/java/com/example/TripController.java",
            "src/main/java/com/example/TripService.java",
            "src/main/java/com/example/TripQueryService.java");
    SourceCodeLocator.Snippet controller = snippets.get(0);
    assertThat(controller.startLine()).isEqualTo(16);
    assertThat(controller.text()).contains("tripService.getTrip(tripId, userId)");
    assertThat(snippets.get(2).text()).contains("tripRepository.findById(tripId)");
  }

  @Test
  void locate_mappingWithoutPath_matchesClassPath() throws Exception {
    writeTripLikeSources();
    SourceCodeLocator locator = new SourceCodeLocator(repo, false);

    List<SourceCodeLocator.Snippet> snippets = locator.locate("POST", "/api/v1/trips/");

    assertThat(snippets).hasSize(1);
    assertThat(snippets.get(0).text()).contains("createTrip()");
  }

  @Test
  void locate_unknownPathOrMethod_returnsEmpty() throws Exception {
    writeTripLikeSources();
    SourceCodeLocator locator = new SourceCodeLocator(repo, false);

    assertThat(locator.locate("GET", "/api/v1/unknown")).isEmpty();
    assertThat(locator.locate("DELETE", "/api/v1/trips/{tripId}")).isEmpty();
  }

  @Test
  void locate_noSourceDirectory_returnsEmpty() throws Exception {
    assertThat(new SourceCodeLocator(repo, false).locate("GET", "/api/v1/trips")).isEmpty();
  }

  // 실제 TripFit 저장소 코드로 확인한다. 리포터 테스트는 incident-reporter/에서 돌기 때문에 상위 폴더가 저장소 루트다.
  // Controller·facade 구조가 바뀌어 이 테스트가 깨지면 SourceCodeLocator의 가정도 함께 점검해야 한다.
  @Test
  void locate_realTripFitSource_findsTripDetailCode() throws Exception {
    Path tripFitRoot = Path.of("..").toAbsolutePath().normalize();
    SourceCodeLocator locator = new SourceCodeLocator(tripFitRoot, false);

    List<SourceCodeLocator.Snippet> snippets = locator.locate("GET", "/api/v1/trips/{tripId}");

    assertThat(snippets).isNotEmpty();
    assertThat(snippets.get(0).file()).endsWith("trip/controller/TripController.java");
    assertThat(snippets).hasSizeGreaterThanOrEqualTo(2);
  }
}
