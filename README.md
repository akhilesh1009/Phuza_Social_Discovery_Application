# location-api

## Simple HTTP API for:

- Saving/looking up bar-like places

- Discovering nearby pubs via Overpass (OpenStreetMap)

- Managing friends

- Creating notifications (Firestore) and sending pushes (FCM)

**Note:** Check-ins and generating pub-hop routes are planned, not implemented yet.

## What’s in scope now

- Locations

  - Save or fetch bar-like places (Firestore)

- Discovery & Routes

    - Find nearby bar-type venues with Overpass OSM

    - Return a quick greedy order or an optimal route

- Friends & Notifications

    - Friend links (Realtime Database)

    - Firestore notifications + push to all of a user’s devices (FCM)


## How the API identifies users

- Send a header:

    - `x-user-id: <uid>`

- If omitted, a default demo-user is assumed (development convenience).

## Endpoints (overview)

- Health

    - `GET /`

    - Returns a basic health payload.

- Locations

  - `POST /api/locations`

    - Search mode when you send a query → calls Mapbox, returns matching POIs (not auto-saved).

    - Save/lookup mode when you send a place object → validates “bar-like” and saves to Firestore if applicable.

  - `GET /api/locations`

    - Lists saved locations.

  - `GET /api/locations/:id`

    - Returns one saved location by id.

## Discovery & Routes

- `POST /api/discover-pubs`

  - Uses Overpass around an origin point; saves bar-like venues; returns a greedy nearest-neighbor hop order.

- `POST /api/discover-route`

  - Same discovery step, then builds an optimal route.

- `POST /api/generate-route`

  - You provide a list of pubs; returns an optimal route.

## Friends & Notifications

- `GET /api/friends`

  - Lists users from RTDB /users.

- `POST /api/friends/:friendId`

  - Creates a one-way friend link for the caller, writes a Notification for friendId, sends FCM to their devices.

- `POST /api/friends/:friendId/accept`

  - Creates a mutual link and notifies the original requester.

- `DELETE /api/friends/:friendId`

  - Removes your one-way link.

- Planned (not live)

    - Any `/api/checkins`* routes are future work.

## Data structures
**Firestore:** `locations/{id}`

```

{
  "_id": "firestore-doc-id",
  "name": "Marble Bar",
  "name_lc": "marble bar",
  "address": "123 Street, City",
  "latitude": -26.145,
  "longitude": 28.041,
  "amenity": "bar",     // bar | pub | brewery | nightclub | restaurant | cafe
  "createdAt": "server timestamp"
}

```
**Firestore:** `Notifications/{userId}/items/{id}`
```
{
  "type": "follow_request",   // follow_request | follow_accept | notification
  "fromUid": "alice",
  "message": "sent you a friend request",
  "createdAt": "server timestamp",
  "read": false
}
```

**Firestore:** `users/{uid}/fcmTokens/{token}`
```
{
  "createdAt": 1727680000000
}
```
**Realtime Database (RTDB):** `/users/{uid}`
```
{
  "displayName": "Alice",
  "photoURL": "https://...",
  "...": "your other profile fields"
}
```

**Realtime Database (RTDB):** `/users/{uid}/friends/{friendId} = true`
```
{
  "users": {
    "alice": {
      "friends": {
        "bob": true
      }
    }
  }
}
```

**Planned (future):** Check-ins (proposed Firestore shape)
```
{
  "checkins": {
    "{checkinId}": {
      "userId": "alice",
      "locationId": "firestore-locations-doc-id",
      "message": "Here for a drink!",
      "visibility": "friends",      
      "createdAt": "server timestamp",
      "expiresAt": "server timestamp",
      "active": true
    }
  }
}
```

## What makes a place “bar-like”

**Positive signals**

 - OSM tags like amenity=bar, amenity=pub, amenity=nightclub, or craft=brewery

- Names/categories containing terms such as: bar, pub, tavern, cocktail bar, wine bar, beer bar, sports bar, tiki bar, lounge, etc.

**Negative filters**

- Avoid false positives like barber, barista, hair bar, brow bar.

If a place isn’t bar-like:

- It may be accepted in the response, but it is not persisted to Firestore.

## How Overpass is used

**Input**

- An origin point (latitude/longitude) and a radius (e.g., 10 km).

**Query**

- The API queries Overpass for OpenStreetMap features near that origin with tags such as:

- `amenity=bar`

- `amenity=pub`

- `amenity=nightclub`

- `craft=brewery`

**Filter & Save**

- Results are filtered by the “bar-like” rules above.

- Valid venues are enriched (address/amenity when needed) and saved to Firestore.

**Output**

- `/discover-pubs`: returns a greedy nearest-neighbor list and total distance.

- `/discover-route`: returns an optimal route with total distance.

## How notifications & pushes work

- **Client responsibility**

  - Store device tokens under users/{uid}/fcmTokens/{token} (token is the doc id).

- **Server flow**

  - On events like friend request/accept:

  - Write a Notification doc under Notifications/{userId}/items.

  - Read all tokens in users/{userId}/fcmTokens/*.

  - Send FCM to those tokens (and prune invalid ones).

- **Benefit**

  - You get an in-app inbox (Firestore) and real-time push (FCM) together.

## Current scope vs future

- **Current**

  - Locations, discovery, routes, friends, notifications, push test.

- **Future**

  - Check-ins (creation, TTL/expiry, visibility, nearby alerts).