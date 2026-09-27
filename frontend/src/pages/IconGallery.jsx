import Icon, { ICONS } from '../components/icons';

/**
 * Dev-only inspection page for the icon set (see App.jsx route guard).
 *
 * Hand-authored SVG path data cannot be verified by reading it, so every icon
 * is rendered at the sizes it is used at, on both the light surface and the
 * dark navigation frame.
 */
export default function IconGallery() {
  return (
    <div className="page">
      <div className="page-header">
        <h1>Icon set</h1>
        <p>
          Sixteen icons on a 24px grid, 1.5px stroke. Check each shape is
          recognisable at 20px — that is the size the rail uses.
        </p>
      </div>

      <div className="card gallery">
        <div className="card-header">
          <h3>On light surface</h3>
        </div>
        <div className="gallery__grid">
          {Object.keys(ICONS).map((name) => (
            <div key={name} className="gallery__cell">
              <Icon name={name} size={20} />
              <Icon name={name} size={32} />
              <span className="gallery__name">{name}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card gallery gallery--frame">
        <div className="card-header">
          <h3>On navigation frame</h3>
        </div>
        <div className="gallery__grid">
          {Object.keys(ICONS).map((name) => (
            <div key={name} className="gallery__cell">
              <Icon name={name} size={20} />
              <span className="gallery__name">{name}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
