# New Features (Android)

I've made some changes to the Android code to reflect some needed features for my use case.

I've made it behave in a generic way to not bind the code to my specific business case, so that it could be reused by anyone.

---

## Features:

### 1. Different titles when there is more than 1 notification with the same `notId`

```javascript
data: {
    notId: '1',
    titleSingle: 'New Notification',
    title: 'New Notifications',
    ...
}
```

### 2. The `summaryText` will be shown in collapsed notifications when there is more than one grouped together

```javascript
data: {
    notId: '1',
    style: 'inbox',
    message: 'Notification 1',
    summaryText: 'You have %n% new notifications',
    ...
}
```

```javascript
data: {
    notId: '1',
    style: 'inbox',
    message: 'Notification 2',
    summaryText: 'You have %n% new notifications',
    ...
}
```

When the notification is collapsed you will see body as: 

**Body**
<pre>
You have 2 new notifications.
</pre>

When the notification is expanded you will see the body as:

**Body**
<pre>
Notification 2
Notification 1
</pre>

(in the reverse order, as it already does currently)

**Summary**
<pre>
You have 2 new notifications.
</pre>

### 3. Message prefix 

Will be shown **right before the message** (as a part of the body) according to the number of notifications.

```javascript
data: {
    notId: '2',
    style: 'inbox',
    titleSingle: 'Project 01',
    title: 'New Projects',
    messagePrefixSingle: '',
    messagePrefix: '<b>Project 01</b>: ',
    message: 'Project 01 description...',
    summaryText: 'You have %n% new projects',
    ...
}
```

```javascript
data: {
    notId: '2',
    style: 'inbox',
    titleSingle: 'Project 02',
    title: 'New Projects',
    messagePrefixSingle: '',
    messagePrefix: '<b>Project 02</b>: ',
    message: 'Project 02 description...',
    summaryText: 'You have %n% new projects',
    ...
}
```

When the first notification arrives, you will see: 

**Title**
<pre>
Project 01
</pre>

**Body**
<pre>
Project 01 description...
</pre>

When the other notification arrives, you will see when the notification is collapsed: 

**Title**
<pre>
New Projects
</pre>

**Body**
<pre>
You have 2 new projects.
</pre>

and when it is expanded:

**Title**
<pre>
New Projects
</pre>

**Body**
<pre>
<b>Project 02</b>: Project 02 description...
<b>Project 01</b>: Project 01 description...
</pre>

**Summary**
<pre>
You have 2 new projects.
</pre>

### 4. Different images when there is more than 1 notification with the same `notId`

```javascript
data: {
    notId: '1',
    imageSingle: 'https//some-site.com/specific-notification-image.png',
    image: 'https//some-site.com/general-notification-image.png',
    ...
}
```

### 5. Possibility to delete notifications with the `notId` specified

```javascript
data: {
    notId: '1',
    delete: 'true',
    ...
}
```

You can also delete only when there is a single notification with the `notId`:

```javascript
data: {
    notId: '1',
    deleteSingle: 'true',
    ...
}
```

### 6. Change the sound and vibration for different notifications with the same `notId`

```javascript
data: {
    notId: '1',
    sound: null,
    vibrate: null,
    soundRecurrent: '',
    vibrateRecurrent: '',
    ...
}
```

(I did it to respect the current behaviour, in which a null/missing property of sound or vibration will execute the default one, whereas an empty string won't execute them. In the above case, a new notification with some `notId` will make the default sound and vibration, but a new one (assuming you haven't dismissed the previous one) with the same `notId` won't execute them, avoiding annoying similar new notifications repeatedly)

### 7. Subgroup (`subId`) for notifications with the same `notId`

This is a **very important feature** in cases in which notifications may be related to other notifications in the same group (`notId`).

A very common case is **chat messages**. You may specify some `notId` (*like `3`, for example*) to group the messages. But the messages may belong to the same talk.

The field `subId` is the talk id, in this case.

**So you may want to display:**

- **When there is 1 message only:** the user name in the notification title, the user image in the notification image and the message content in the notification body.

- **When there is more than 1 message of the same talk, but only 1 talk:** the user name in the notification title, the user image in the notification image and the messages, one message per line, in the notification body, when expanded (assuming a talk of only 2 people, or the group name in the title, in group chat). The summary will be `[User Name] has sent %n% new messages.` (in a group it could be `%n% new messages in the group [Group Name].`)

- **When there is more than 1 talk:** show `New Messages` in the title, some generic image in the notification image and the user name together with the message content, one in each line, in the notification body, when expanded. The summary will be `You have %n% new messages in %m% talks`, where `%m%` is the number of different push notifications `subIds` with that `notId` (**in this case, the number of different talks**).

```javascript
data: {
    notId: '3',
    subId: '1',
    titleSingle: 'User 01',
    titleSubSingle: 'User 01',
    title: 'New Messages',
    imageSingle: '/user/user-01.png',
    imageSubSingle: '/user/user-01.png',
    image: '/icon/new-messages.png',
    style: 'inbox',
    messagePrefixSingle: '',
    messagePrefixSubSingle: '',
    messagePrefix: '<b>User 01</b>: ',
    message: 'Message 01 description...',
    summaryTextSubSingle: 'User 01 has sent %n% new messages.',
    summaryText: 'You have %n% new messages in %m% talks.',
    ...
}
```

```javascript
data: {
    notId: '3',
    subId: '1',
    titleSingle: 'User 01',
    titleSubSingle: 'User 01',
    title: 'New Messages',
    imageSingle: '/user/user-01.png',
    imageSubSingle: '/user/user-01.png',
    image: '/icon/new-messages.png',
    style: 'inbox',
    messagePrefixSingle: '',
    messagePrefixSubSingle: '',
    messagePrefix: '<b>User 01</b>: ',
    message: 'Message 02 description...',
    summaryTextSubSingle: 'User 01 has sent %n% new messages.',
    summaryText: 'You have %n% new messages in %m% talks.',
    ...
}
```

```javascript
data: {
    notId: '3',
    subId: '2',
    titleSingle: 'User 02',
    titleSubSingle: 'User 02',
    title: 'New Messages',
    imageSingle: '/user/user-02.png',
    imageSubSingle: '/user/user-02.png',
    image: '/icon/new-messages.png',
    style: 'inbox',
    messagePrefixSingle: '',
    messagePrefixSubSingle: '',
    messagePrefix: '<b>User 02</b>: ',
    message: 'Message 03 description...',
    summaryTextSubSingle: 'User 02 has sent %n% new messages.',
    summaryText: 'You have %n% new messages in %m% talks.',
    ...
}
```

When the **1st** notification arrives, you will see: 

**Title**
<pre>
User 01
</pre>

**Image**
<pre>
/user/user-01.png
</pre>

**Body**
<pre>
Message 01 description...
</pre>

When the **2nd** notification arrives, you will see when the notification is expanded: 

**Title**
<pre>
User 01
</pre>

**Image**
<pre>
/user/user-01.png
</pre>

**Body**
<pre>
Message 02 description...
Message 01 description...
</pre>

**Summary**
<pre>
User 01 has sent 2 new messages.
</pre>

When the **3rd** notification arrives, you will see when the notification is expanded: 

**Title**
<pre>
New Messages
</pre>

**Image**
<pre>
/icon/new-messages.png
</pre>

**Body**
<pre>
<b>User 02</b>: Message 03 description...
<b>User 01</b>: Message 02 description...
<b>User 01</b>: Message 01 description...
</pre>

**Summary**
<pre>
You have 3 new messages in 2 talks.
</pre>

### 8. Group notifications together when received in the App

This is a **very important feature** that I did because **when the user clicks the push notification**, either with the app opened or closed, when the App receives the event of the push notification I want that the event contains **all notifications that were collapsed together** (not just the last).

Before, I was using `content-available: '1'` with some hacks to store temporarily the notifications, and the 2nd time the notification was received I was sending all of the notifications stored as a single one containing all of them and then I run the app action.

I think I don't need to say that this is very error prone, like in case the user dismiss a notification, then receives another and when click the notification the app will use the dismissed notification together with the new one, because it had stored it previously (the app doesn't know it was dismissed).

Not only that, but I had to resort to all kinds of hackish solutions to make it work, store temporarily, verify the 2nd time it was received, verify if app was closed when the notification was clicked, and it still had errors (like the one I said before).

Instead, I changed the code, so that when the push notification is clicked, it will send an object like:

```typescript
interface PushNotificationMain {
    last: NotificationEventResponse;
    list: Array<NotificationEventResponse>;
}
```

where `NotificationEventResponse` is the type of object with the properties `message`, `additionalData`, etc...

The property `last` is the **last push notification** (that is, the object that was sent in the event before I did this change).

The property `list` is a list with **all push notifications** in the group (collapsed together), including the last.

**So I use it like:**

*Without observables:*

```typescript
pushObject.on('mainNotification', data => {
    console.log('data', data);
    console.log('last', data.last);
    console.log('list', data.list);
});
```

*With observables:*

```typescript
pushObject.on('mainNotification').subscribe(data => {
    console.log('data', data);
    console.log('last', data.last);
    console.log('list', data.list);
});
```

(**Take note that I use `mainNotification` instead of `notification`**)

This is very helpful in cases like opening a **specific talk page** when all notifications have the **same talk id**, or open a **talk list page** when there are **more than 1  talk** in the list.

Using it this way I achieved what I wanted without any hackish solutions. No problems so far.
