import { haversineKm } from "./geo.js"; // import Haversine distance in km (Veness 2019)

// Generate a simple nearest-neighbor route for bar hopping
export function generateOptimalRoute(startingPoint, bars) {
  // handle case when there are no bars
  if (!bars || bars.length === 0) {
    return {
      route: [startingPoint],
      totalDistance: 0,
      estimatedTime: 0,
      routeDetails: [
        {
          order: 1,
          name: startingPoint.name,
          address: startingPoint.address,
          coordinates: startingPoint.coordinates,
          distanceFromPrevious: 0, // first point has zero distance
        },
      ],
    };
  }

  const route = [startingPoint]; // final route list
  const unvisited = [...bars];   // bars still to visit
  let currentLocation = startingPoint; // start here
  let totalDistance = 0; // total distance in km

  // repeatedly pick the nearest unvisited bar (nearest-neighbor heuristic)
  while (unvisited.length > 0) {
    let nearestBar = null;
    let nearestDistance = Number.POSITIVE_INFINITY;
    let nearestIndex = -1;

    for (let i = 0; i < unvisited.length; i++) {
      const bar = unvisited[i];

      // calculate distance between current and next bar using Haversine (Veness 2019)
      const distance = haversineKm(
        { lat: currentLocation.coordinates.latitude, lon: currentLocation.coordinates.longitude },
        { lat: bar.coordinates.latitude,            lon: bar.coordinates.longitude }
      );

      // update if this bar is closer
      if (distance < nearestDistance) {
        nearestDistance = distance;
        nearestBar = bar;
        nearestIndex = i;
      }
    }

    // add the closest bar to the route
    route.push(nearestBar);
    totalDistance += nearestDistance;
    currentLocation = nearestBar;
    unvisited.splice(nearestIndex, 1); // remove from list
  }

  // estimate time assuming walking speed = 5 km/h; round minutes (MDN 2025a)
  const estimatedTimeHours = totalDistance / 5;
  const estimatedTimeMinutes = Math.round(estimatedTimeHours * 60); // (MDN 2025a)

  // return full route details
  return {
    route,
    totalDistance: Math.round(totalDistance * 100) / 100, // 2 decimal places (MDN 2025a)
    estimatedTime: estimatedTimeMinutes, // total time in minutes
    routeDetails: route.map((location, index) => ({
      order: index + 1,
      name: location.name,
      address: location.address,
      coordinates: location.coordinates,
      // segment distance via Haversine between consecutive points (Veness 2019)
      distanceFromPrevious:
        index === 0
          ? 0
          : haversineKm(
              { lat: route[index - 1].coordinates.latitude, lon: route[index - 1].coordinates.longitude },
              { lat: location.coordinates.latitude,          lon: location.coordinates.longitude }
            ),
    })),
  };
}

/*
 * REFERENCES

 * Android Developers. 2025. 
 * “Create and Manage Notification Channels”. 
 * <https://developer.android.com/develop/ui/views/notifications/channels> 
 * [accessed 24 September 2025].
 * 
 * Firebase. 2025a. 
 * “Add the Firebase Admin SDK to Your Server”.
 * <https://firebase.google.com/docs/admin/setup> 
 * [accessed 20 September 2025].
 * 
 * Firebase. 2025b. 
 * “Perform Simple and Compound Queries in Cloud Firestore | Firebase Documentation”. 
 * <https://firebase.google.com/docs/firestore/query-data/queries> 
 * [accessed 20 September 2025].
 * 
 * Firebase. 2025c. 
 * “Send a Message Using Firebase Admin SDK | Firebase Cloud Messaging”. 
 * <https://firebase.google.com/docs/cloud-messaging/send/admin-sdk> 
 * [accessed 23 September 2025].
 * 
 * GeeksforGeeks. 2022. 
 * “How to Get Currently Signed in User in Web and Firebase ?” 
 * <https://www.geeksforgeeks.org/firebase/how-to-get-currently-signed-in-user-in-web-and-firebase/> 
 * [accessed 25 September 2025].
 * 
 * Manico, Jim and August Detlefsen. 2015. Iron-Clad Java: Building Secure Web Applications. New York, N.Y. Mcgraw-Hill Education.
 * 
 * Mapbox. 2025. 
 * “Geocoding | API”. 
 * <https://docs.mapbox.com/api/search/geocoding/>
 *  [accessed 23 September 2025].
 * 
 * MDN. 2019.
 * “JSON.parse()”.
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/JSON/parse>
 * [accessed 5 October 2025].
 * 
 * MDN. 2024. 
 * “How to Use Promises - Learn Web Development | MDN”.
 * <https://developer.mozilla.org/en-US/docs/Learn_web_development/Extensions/Async_JS/Promises#async_and_await>
 * [accessed 19 September 2025].
 * 
 * MDN. 2025a.
 * “Math - JavaScript | MDN”.
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Math>
 * [accessed 21 September 2025].
 * 
 * MDN. 2025b.
 * “Object.entries() - JavaScript | MDN”. 
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Object/entries> 
 * [accessed 18 September 2025].
 * 
 * MDN. 2025c. 
 * “RegExp.prototype.test() - JavaScript | MDN”. 
 * <https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/RegExp/test> 
 * [accessed 19 September 2025].
 * 
 * Overpass. 2011. 
 * “Overpass API - OpenStreetMap Wiki”. 
 * <https://wiki.openstreetmap.org/wiki/Overpass_API>
 * [accessed 23 September 2025].
 * 
 * Overpass. 2022.
 * “Key:amenity - OpenStreetMap Wiki”.
 * <https://wiki.openstreetmap.org/wiki/Key:amenity>
 * [accessed 28 September 2025].
 * 
 * Veness, Chris. 2019. 
 * “Calculate Distance and Bearing between Two Latitude/Longitude Points Using Haversine Formula in JavaScript”.
 * <https://www.movable-type.co.uk/scripts/latlong.html>
 *  [accessed 19 September 2025].
 */
