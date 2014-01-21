# Eclipse Less Plug-in

An Eclipse builder plug-in for precompiling LESS files.

## Installation

1. Install [Node.js](http://nodejs.org/).
2. Open Eclipse and go to `Help`-`Install New Software`.
3. Add the update site: http://eclipse-update.palantir.com/eclipse-less/.
4. Reboot Eclipse.

### Enabling the Builder

1. Right-click on a project containing LESS files.
2. Select `Configure`-`Enable LESS Builder`.

#### Configuring the Builder

1. Create `your-project/.settings/com.palantir.less.lessBuilder.prefs`
2. Add Eclipse style key/value pairs for src and dest, for the root less file and the output css file respectively.

A `.settings/com.palantir.less.lessBuilder.prefs` might look like
```
src=src/resources/app.less
dest=src/resources/app.css
```

## Development

1. `git clone git@github.com:palantir/eclipse-less.git`
2. Run `npm install --prefix com.palantir.less com.palantir.less` in the root directory of the project to install npm dependencies.
3. In Eclipse, right-click on the `eclipse-less` project and select `Debug As` - `Eclipse Application`.

## Building the Eclipse Update Site

```
grunt
mvn clean install
```
The update site will be in `com.palantir.less.p2updatesite/target/repository`.
