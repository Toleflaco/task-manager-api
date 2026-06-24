package com.mtole.taskmanager.users;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.mtole.taskmanager.users.UserTestDataBuilder.aUser;

@DisplayName("User entity")
class UserTest {

    @Nested
    @DisplayName("equals and hashCode (Vlad Mihalcea pattern)")
    class EqualsAndHashCode {

        @Test
        @DisplayName("Two users with the same ID are equal, even if they have different fields")
        void twoUsersWithSameId_AreEquals() {

            //Arrange
            User user1 = aUser().withId(1L).build();
            User user2 = aUser().withId(1L).withEmail("pruebatest@test.com").build();
            //Act +Assert
            assertThat(user1).isEqualTo(user2);
        }

        @Test
        @DisplayName("two users with different ID are not equal, even if they have same fields")
        void twoUsersWithDifferentId_AreNotEqual() {
            User user1 = aUser().withId(1L).build();
            User user2 = aUser().withId(2L).build();

            assertThat(user1).isNotEqualTo(user2);
        }

        @Test
        @DisplayName("A transient user (id null) is not equal to a persisted user")
        void transientUser_isNotEqualToPersistedUser() {
            User transientUser = aUser().build();
            User persistedUser = aUser().withId(1L).build();

            assertThat(transientUser).isNotEqualTo(persistedUser);
        }

        @Test
        @DisplayName("Users that are equal have the same hashCode (Java contract)")
        void usersThatAreEqual_haveSameHashCode() {
            User user1 = aUser().withId(1L).build();
            User user2 = aUser().withId(1L).build();
            assertThat(user1.hashCode()).isEqualTo(user2.hashCode());
        }

        @Test
        @DisplayName("hashCode stays stable when id is assigned after persistence (Vlad Mihalcea critical invariant)")
        void hashCode_isStableWhenIdIsAssigned() {
            User user = aUser().build();
            int hashBeforeId = user.hashCode();

            user.setId(1L);
            int hashAfterId = user.hashCode();

            assertThat(hashBeforeId).isEqualTo(hashAfterId);

        }
    }
}


