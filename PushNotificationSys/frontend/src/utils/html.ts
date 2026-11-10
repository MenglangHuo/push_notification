/**
 * Converts rich HTML content into clean, human-readable plain text
 * suitable for preview snippets and cards.
 *
 * @param html The HTML string (or plain text)
 * @param maxLength Optional maximum length of the snippet (defaults to 120)
 * @returns Clean, truncated plain text without HTML tags
 */
export function formatHtmlPreview(html?: string | null, maxLength = 120): string {
  if (!html) return ''

  // Check if string contains any HTML tags
  const hasHtml = /<[a-z][\s\S]*>/i.test(html)
  let text = html

  if (hasHtml) {
    // 1. Replace block-level tags and line breaks with a space to avoid words colliding
    text = text
      .replace(/<\/(p|div|li|h[1-6]|tr|blockquote|section|article)>/gi, ' ')
      .replace(/<br\s*[/]?>/gi, ' ')

    // 2. Strip remaining HTML tags
    text = text.replace(/<[^>]+>/g, '')

    // 3. Decode common HTML entities
    text = text
      .replace(/&nbsp;/gi, ' ')
      .replace(/&amp;/gi, '&')
      .replace(/&lt;/gi, '<')
      .replace(/&gt;/gi, '>')
      .replace(/&quot;/gi, '"')
      .replace(/&#39;/gi, "'")
  }

  // 4. Normalize multiple whitespaces/newlines into a single space
  text = text.replace(/\s+/g, ' ').trim()

  if (!text) return ''

  // 5. Apply character limit if text exceeds maxLength
  if (maxLength > 0 && text.length > maxLength) {
    return text.slice(0, maxLength).trim() + '...'
  }

  return text
}
